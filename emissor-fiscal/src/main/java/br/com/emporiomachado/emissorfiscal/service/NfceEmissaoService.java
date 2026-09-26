package br.com.emporiomachado.emissorfiscal.service;

import br.com.emporiomachado.emissorfiscal.config.FiscalProperties;
import br.com.emporiomachado.emissorfiscal.config.NfeConfigFactory;
import br.com.emporiomachado.emissorfiscal.domain.NotaFiscal;
import br.com.emporiomachado.emissorfiscal.domain.NotaFiscalRepository;
import br.com.emporiomachado.emissorfiscal.domain.NotaStatus;
import br.com.emporiomachado.emissorfiscal.dto.EmitirNotaRequest;
import br.com.emporiomachado.emissorfiscal.exception.FiscalException;
import br.com.swconsultoria.nfe.Nfe;
import br.com.swconsultoria.nfe.dom.ConfiguracoesNfe;
import br.com.swconsultoria.nfe.dom.enuns.DocumentoEnum;
import br.com.swconsultoria.nfe.dom.enuns.ServicosEnum;
import br.com.swconsultoria.nfe.dom.enuns.StatusEnum;
import br.com.swconsultoria.nfe.schemas.TEnviNFe;
import br.com.swconsultoria.nfe.schemas.TRetConsReciNFe;
import br.com.swconsultoria.nfe.schemas.TRetEnviNFe;
import br.com.swconsultoria.nfe.util.NFCeUtil;
import br.com.swconsultoria.nfe.util.RetornoUtil;
import br.com.swconsultoria.nfe.util.WebServiceUtil;
import br.com.swconsultoria.nfe.util.XmlNfeUtil;
import br.com.swconsultoria.nfe.schemas.TNFe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NfceEmissaoService {

    private static final Logger log = LoggerFactory.getLogger(NfceEmissaoService.class);

    private final FiscalProperties properties;
    private final NfeConfigFactory configFactory;
    private final NfceXmlBuilder xmlBuilder;
    private final NumeracaoService numeracaoService;
    private final NotaFiscalRepository repository;

    public NfceEmissaoService(
            FiscalProperties properties,
            NfeConfigFactory configFactory,
            NfceXmlBuilder xmlBuilder,
            NumeracaoService numeracaoService,
            NotaFiscalRepository repository
    ) {
        this.properties = properties;
        this.configFactory = configFactory;
        this.xmlBuilder = xmlBuilder;
        this.numeracaoService = numeracaoService;
        this.repository = repository;
    }

    @Transactional
    public NotaFiscal emitir(EmitirNotaRequest request) {
        if (!properties.isHomologacao()) {
            throw new FiscalException("v1 permite apenas AMBIENTE=homologacao");
        }

        int serie = properties.serie();
        long numero = numeracaoService.proximoNumero(serie);

        NotaFiscal nota = new NotaFiscal();
        nota.setSerie(serie);
        nota.setNumero(numero);
        nota.setStatus(NotaStatus.ENVIANDO);
        nota.setPedidoExternoId(request.pedidoId());
        nota = repository.save(nota);

        try {
            ConfiguracoesNfe config = configFactory.criar();
            NfceXmlBuilder.BuildResult built = xmlBuilder.build(config, request, numero);
            nota.setChaveAcesso(built.chave());
            nota.setValorTotal(built.valorNf());

            TEnviNFe enviNFe = new TEnviNFe();
            enviNFe.setVersao(ConstantesUtilVersao.NFE);
            enviNFe.setIdLote(String.valueOf(numero));
            enviNFe.setIndSinc("1");
            enviNFe.getNFe().add(built.nfe());

            enviNFe = Nfe.montaNfe(config, enviNFe, properties.validarXml());

            String qrCode = montarQrCode(enviNFe, config);
            TNFe.InfNFeSupl supl = new TNFe.InfNFeSupl();
            supl.setQrCode(qrCode);
            supl.setUrlChave(WebServiceUtil.getUrl(config, DocumentoEnum.NFCE, ServicosEnum.URL_CONSULTANFCE));
            enviNFe.getNFe().get(0).setInfNFeSupl(supl);
            nota.setQrCode(qrCode);
            nota.setXmlEnvio(XmlNfeUtil.objectToXml(enviNFe));

            TRetEnviNFe retorno = Nfe.enviarNfe(config, enviNFe, DocumentoEnum.NFCE);
            processarRetorno(nota, config, enviNFe, retorno);
            return repository.save(nota);
        } catch (FiscalException ex) {
            nota.setStatus(NotaStatus.ERRO);
            nota.setXMotivo(ex.getMessage());
            repository.save(nota);
            throw ex;
        } catch (Exception ex) {
            log.error("Falha ao emitir NFC-e nº {}", numero, ex);
            nota.setStatus(NotaStatus.ERRO);
            nota.setXMotivo(ex.getMessage());
            repository.save(nota);
            throw new FiscalException("Falha na emissão: " + ex.getMessage(), ex);
        }
    }

    private void processarRetorno(NotaFiscal nota, ConfiguracoesNfe config, TEnviNFe enviNFe, TRetEnviNFe retorno)
            throws Exception {
        if (RetornoUtil.isRetornoAssincrono(retorno)) {
            String recibo = retorno.getInfRec().getNRec();
            TRetConsReciNFe retornoNfe = null;
            for (int tentativa = 0; tentativa < 15; tentativa++) {
                retornoNfe = Nfe.consultaRecibo(config, recibo, DocumentoEnum.NFCE);
                if (StatusEnum.LOTE_EM_PROCESSAMENTO.getCodigo().equals(retornoNfe.getCStat())) {
                    Thread.sleep(1000);
                } else {
                    break;
                }
            }
            if (retornoNfe == null) {
                throw new FiscalException("Sem retorno do recibo SEFAZ");
            }
            try {
                RetornoUtil.validaAssincrono(retornoNfe);
                var prot = retornoNfe.getProtNFe().get(0);
                nota.setCStat(prot.getInfProt().getCStat());
                nota.setXMotivo(prot.getInfProt().getXMotivo());
                nota.setProtocolo(prot.getInfProt().getNProt());
                nota.setXmlAutorizado(XmlNfeUtil.criaNfeProc(enviNFe, prot));
                nota.setStatus(mapStatus(prot.getInfProt().getCStat()));
            } catch (Exception e) {
                if (retornoNfe.getProtNFe() != null && !retornoNfe.getProtNFe().isEmpty()) {
                    var prot = retornoNfe.getProtNFe().get(0);
                    nota.setCStat(prot.getInfProt().getCStat());
                    nota.setXMotivo(prot.getInfProt().getXMotivo());
                    nota.setStatus(mapStatus(prot.getInfProt().getCStat()));
                } else {
                    nota.setCStat(retornoNfe.getCStat());
                    nota.setXMotivo(retornoNfe.getXMotivo());
                    nota.setStatus(NotaStatus.REJEITADA);
                }
                throw new FiscalException(
                        "SEFAZ rejeitou: " + nota.getCStat() + " - " + nota.getXMotivo(), e);
            }
        } else {
            try {
                RetornoUtil.validaSincrono(retorno);
                nota.setCStat(retorno.getProtNFe().getInfProt().getCStat());
                nota.setXMotivo(retorno.getProtNFe().getInfProt().getXMotivo());
                nota.setProtocolo(retorno.getProtNFe().getInfProt().getNProt());
                nota.setXmlAutorizado(XmlNfeUtil.criaNfeProc(enviNFe, retorno.getProtNFe()));
                nota.setStatus(mapStatus(retorno.getProtNFe().getInfProt().getCStat()));
            } catch (Exception e) {
                if (retorno.getProtNFe() != null && retorno.getProtNFe().getInfProt() != null) {
                    nota.setCStat(retorno.getProtNFe().getInfProt().getCStat());
                    nota.setXMotivo(retorno.getProtNFe().getInfProt().getXMotivo());
                    nota.setStatus(mapStatus(retorno.getProtNFe().getInfProt().getCStat()));
                } else {
                    nota.setCStat(retorno.getCStat());
                    nota.setXMotivo(retorno.getXMotivo());
                    nota.setStatus(NotaStatus.REJEITADA);
                }
                throw new FiscalException(
                        "SEFAZ rejeitou: " + nota.getCStat() + " - " + nota.getXMotivo(), e);
            }
        }
    }

    private String montarQrCode(TEnviNFe enviNFe, ConfiguracoesNfe config) throws Exception {
        String chave = enviNFe.getNFe().get(0).getInfNFe().getId().substring(3);
        // QR Code NFC-e v3 (online) — CSC permanece cadastrado na SEFAZ; V3 não embute CSC na URL.
        // Contingência offline ainda usa idToken + CSC (vide NFCeUtil.getCodeQRCodeContingencia*).
        return NFCeUtil.getCodeQRCodeV3(
                chave,
                config.getAmbiente().getCodigo(),
                WebServiceUtil.getUrl(config, DocumentoEnum.NFCE, ServicosEnum.URL_QRCODE)
        );
    }

    private NotaStatus mapStatus(String cStat) {
        if ("100".equals(cStat) || "150".equals(cStat)) {
            return NotaStatus.AUTORIZADA;
        }
        if ("110".equals(cStat) || "301".equals(cStat) || "302".equals(cStat)) {
            return NotaStatus.DENEGADA;
        }
        return NotaStatus.REJEITADA;
    }

    /** Evita import estático frágil se ConstantesUtil.VERSAO mudar de package. */
    private static final class ConstantesUtilVersao {
        static final String NFE = br.com.swconsultoria.nfe.util.ConstantesUtil.VERSAO.NFE;
    }
}
