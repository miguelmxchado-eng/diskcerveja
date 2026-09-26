package br.com.emporiomachado.emissorfiscal.service;

import br.com.emporiomachado.emissorfiscal.config.FiscalProperties;
import br.com.emporiomachado.emissorfiscal.dto.EmitirNotaRequest;
import br.com.emporiomachado.emissorfiscal.exception.FiscalException;
import br.com.swconsultoria.nfe.dom.ConfiguracoesNfe;
import br.com.swconsultoria.nfe.dom.enuns.DocumentoEnum;
import br.com.swconsultoria.nfe.schemas.TEnderEmi;
import br.com.swconsultoria.nfe.schemas.TNFe;
import br.com.swconsultoria.nfe.schemas.TNFe.InfNFe;
import br.com.swconsultoria.nfe.schemas.TNFe.InfNFe.Det;
import br.com.swconsultoria.nfe.schemas.TNFe.InfNFe.Det.Imposto;
import br.com.swconsultoria.nfe.schemas.TNFe.InfNFe.Det.Imposto.COFINS;
import br.com.swconsultoria.nfe.schemas.TNFe.InfNFe.Det.Imposto.COFINS.COFINSOutr;
import br.com.swconsultoria.nfe.schemas.TNFe.InfNFe.Det.Imposto.ICMS;
import br.com.swconsultoria.nfe.schemas.TNFe.InfNFe.Det.Imposto.ICMS.ICMSSN102;
import br.com.swconsultoria.nfe.schemas.TNFe.InfNFe.Det.Imposto.PIS;
import br.com.swconsultoria.nfe.schemas.TNFe.InfNFe.Det.Imposto.PIS.PISOutr;
import br.com.swconsultoria.nfe.schemas.TNFe.InfNFe.Det.Prod;
import br.com.swconsultoria.nfe.schemas.TNFe.InfNFe.Emit;
import br.com.swconsultoria.nfe.schemas.TNFe.InfNFe.Ide;
import br.com.swconsultoria.nfe.schemas.TNFe.InfNFe.Pag;
import br.com.swconsultoria.nfe.schemas.TNFe.InfNFe.Total;
import br.com.swconsultoria.nfe.schemas.TNFe.InfNFe.Total.ICMSTot;
import br.com.swconsultoria.nfe.schemas.TNFe.InfNFe.Transp;
import br.com.swconsultoria.nfe.schemas.TUfEmi;
import br.com.swconsultoria.nfe.util.ChaveUtil;
import br.com.swconsultoria.nfe.util.ConstantesUtil;
import br.com.swconsultoria.nfe.util.XmlNfeUtil;
import org.springframework.stereotype.Component;

import javax.xml.bind.JAXBElement;
import javax.xml.namespace.QName;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Monta TNFe modelo 65 (NFC-e 4.00) a partir do payload de venda.
 */
@Component
public class NfceXmlBuilder {

    private static final String HOMOLOG_XPROD =
            "NOTA FISCAL EMITIDA EM AMBIENTE DE HOMOLOGACAO - SEM VALOR FISCAL";
    private static final String HOMOLOG_XNOME =
            "NF-E EMITIDA EM AMBIENTE DE HOMOLOGACAO - SEM VALOR FISCAL";

    private static final ZoneId ZONA_SEFAZ = ZoneId.of("America/Sao_Paulo");

    private final FiscalProperties properties;

    public NfceXmlBuilder(FiscalProperties properties) {
        this.properties = properties;
    }

    public record BuildResult(TNFe nfe, String chave, String cnf, BigDecimal valorNf) {
    }

    public BuildResult build(ConfiguracoesNfe config, EmitirNotaRequest request, long numero) {
        validarTotais(request);

        // Horário de Brasília — Mac em outro fuso envia dhEmi "atrasada" (rejeição 704).
        LocalDateTime dataEmissao = LocalDateTime.now(ZONA_SEFAZ);
        String cnpj = properties.emitente().cnpjDigits();
        String modelo = DocumentoEnum.NFCE.getModelo();
        int serie = properties.serie();
        String tipoEmissao = "1";
        String cnf = String.format("%08d", new Random().nextInt(99_999_999));

        ChaveUtil chaveUtil = new ChaveUtil(
                config.getEstado(), cnpj, modelo, serie, (int) numero, tipoEmissao, cnf, dataEmissao);
        String chave = chaveUtil.getChaveNF();
        String cdv = chaveUtil.getDigitoVerificador();

        InfNFe infNFe = new InfNFe();
        infNFe.setId(chave);
        infNFe.setVersao(ConstantesUtil.VERSAO.NFE);
        infNFe.setIde(montarIde(config, cnf, numero, tipoEmissao, modelo, serie, cdv, dataEmissao));
        infNFe.setEmit(montarEmitente(config));

        DestinatarioInfo destInfo = montarDestinatario(request);
        if (destInfo.dest() != null) {
            infNFe.setDest(destInfo.dest());
        }

        List<LinhaCalc> linhas = montarItens(request);
        BigDecimal vProd = BigDecimal.ZERO;
        BigDecimal vDesc = BigDecimal.ZERO;
        int nItem = 1;
        for (LinhaCalc linha : linhas) {
            Det det = new Det();
            det.setNItem(String.valueOf(nItem++));
            det.setProd(linha.prod());
            det.setImposto(montarImpostoSimples(linha.origem(), linha.csosn()));
            infNFe.getDet().add(det);
            vProd = vProd.add(linha.vProd());
            vDesc = vDesc.add(linha.vDesc());
        }

        BigDecimal descontoExtra = nz(request.descontoTotal());
        vDesc = vDesc.add(descontoExtra);
        BigDecimal vFrete = nz(request.freteTotal()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal vNf = vProd.subtract(vDesc).add(vFrete).setScale(2, RoundingMode.HALF_UP);
        if (vNf.compareTo(BigDecimal.ZERO) <= 0) {
            throw new FiscalException("Valor da NFC-e deve ser maior que zero");
        }

        infNFe.setTotal(montarTotal(vProd, vDesc, vFrete, vNf));
        infNFe.setTransp(montarTransporte());
        infNFe.setPag(montarPagamento(request, vNf));

        if (request.observacao() != null && !request.observacao().isBlank()) {
            InfNFe.InfAdic infAdic = new InfNFe.InfAdic();
            infAdic.setInfCpl(request.observacao().trim());
            infNFe.setInfAdic(infAdic);
        }

        TNFe nfe = new TNFe();
        nfe.setInfNFe(infNFe);
        return new BuildResult(nfe, chave.substring(3), cnf, vNf);
    }

    private Ide montarIde(ConfiguracoesNfe config, String cnf, long numero, String tipoEmissao,
                          String modelo, int serie, String cdv, LocalDateTime dataEmissao) {
        Ide ide = new Ide();
        ide.setCUF(config.getEstado().getCodigoUF());
        ide.setCNF(cnf);
        ide.setNatOp("VENDA");
        ide.setMod(modelo);
        ide.setSerie(String.valueOf(serie));
        ide.setNNF(String.valueOf(numero));
        ide.setDhEmi(XmlNfeUtil.dataNfe(dataEmissao, ZONA_SEFAZ));
        ide.setTpNF("1");
        ide.setIdDest("1");
        ide.setCMunFG(properties.emitente().codigoMunicipio());
        ide.setTpImp("4");
        ide.setTpEmis(tipoEmissao);
        ide.setCDV(cdv);
        ide.setTpAmb(config.getAmbiente().getCodigo());
        ide.setFinNFe("1");
        ide.setIndFinal("1");
        ide.setIndPres("1");
        ide.setProcEmi("0");
        ide.setVerProc("emissor-fiscal-0.1");
        return ide;
    }

    private Emit montarEmitente(ConfiguracoesNfe config) {
        FiscalProperties.Emitente e = properties.emitente();
        Emit emit = new Emit();
        emit.setCNPJ(e.cnpjDigits());
        emit.setXNome(e.razaoSocial());
        if (e.nomeFantasia() != null && !e.nomeFantasia().isBlank()) {
            emit.setXFant(e.nomeFantasia());
        }

        TEnderEmi ender = new TEnderEmi();
        ender.setXLgr(e.logradouro());
        ender.setNro(e.numero());
        if (e.complemento() != null && !e.complemento().isBlank()) {
            ender.setXCpl(e.complemento());
        }
        ender.setXBairro(e.bairro());
        ender.setCMun(e.codigoMunicipio());
        ender.setXMun(e.municipio());
        ender.setUF(TUfEmi.valueOf(config.getEstado().toString()));
        ender.setCEP(e.cepDigits());
        ender.setCPais("1058");
        ender.setXPais("Brasil");
        if (e.foneDigits() != null && !e.foneDigits().isBlank()) {
            ender.setFone(e.foneDigits());
        }
        emit.setEnderEmit(ender);

        String ie = e.ie() == null ? "" : e.ie().trim();
        if (ie.isEmpty() || "ISENTO".equalsIgnoreCase(ie)) {
            emit.setIE("ISENTO");
        } else {
            emit.setIE(ie.replaceAll("\\D", ""));
        }
        emit.setCRT(e.crt());
        return emit;
    }

    private record DestinatarioInfo(InfNFe.Dest dest) {
    }

    private DestinatarioInfo montarDestinatario(EmitirNotaRequest request) {
        if (request.destinatario() == null) {
            return new DestinatarioInfo(null);
        }
        String doc = request.destinatario().cpfCnpj() == null
                ? ""
                : request.destinatario().cpfCnpj().replaceAll("\\D", "");
        if (doc.isEmpty()) {
            return new DestinatarioInfo(null);
        }

        InfNFe.Dest dest = new InfNFe.Dest();
        if (doc.length() == 11) {
            dest.setCPF(doc);
        } else if (doc.length() == 14) {
            dest.setCNPJ(doc);
        } else {
            throw new FiscalException("CPF/CNPJ do destinatário inválido: " + doc);
        }

        if (properties.isHomologacao()) {
            dest.setXNome(HOMOLOG_XNOME);
        } else if (request.destinatario().nome() != null && !request.destinatario().nome().isBlank()) {
            dest.setXNome(request.destinatario().nome().trim());
        }
        dest.setIndIEDest("9");
        if (request.destinatario().email() != null && !request.destinatario().email().isBlank()) {
            dest.setEmail(request.destinatario().email().trim());
        }
        return new DestinatarioInfo(dest);
    }

    private record LinhaCalc(Prod prod, BigDecimal vProd, BigDecimal vDesc, String origem, String csosn) {
    }

    private List<LinhaCalc> montarItens(EmitirNotaRequest request) {
        List<LinhaCalc> linhas = new ArrayList<>();
        boolean first = true;
        for (EmitirNotaRequest.ItemVendaRequest item : request.itens()) {
            BigDecimal qCom = item.quantidade().setScale(4, RoundingMode.HALF_UP);
            BigDecimal vUn = item.valorUnitario().setScale(4, RoundingMode.HALF_UP);
            BigDecimal vProd = qCom.multiply(vUn).setScale(2, RoundingMode.HALF_UP);
            BigDecimal vDesc = nz(item.desconto()).setScale(2, RoundingMode.HALF_UP);

            Prod prod = new Prod();
            prod.setCProd(item.codigo());
            String ean = (item.ean() == null || item.ean().isBlank()) ? "SEM GTIN" : item.ean().trim();
            prod.setCEAN(ean);
            prod.setCEANTrib(ean);

            String xProd = item.descricao();
            if (properties.isHomologacao() && first) {
                xProd = HOMOLOG_XPROD;
            }
            prod.setXProd(xProd);
            prod.setNCM(item.ncm().replaceAll("\\D", ""));
            prod.setCFOP(item.cfop().replaceAll("\\D", ""));
            prod.setUCom(item.unidade());
            prod.setQCom(qCom.toPlainString());
            prod.setVUnCom(vUn.toPlainString());
            prod.setVProd(vProd.toPlainString());
            prod.setUTrib(item.unidade());
            prod.setQTrib(qCom.toPlainString());
            prod.setVUnTrib(vUn.toPlainString());
            if (vDesc.compareTo(BigDecimal.ZERO) > 0) {
                prod.setVDesc(vDesc.toPlainString());
            }
            prod.setIndTot("1");

            String origem = item.origem() == null || item.origem().isBlank() ? "0" : item.origem();
            String csosn = item.cstOuCsosn() == null || item.cstOuCsosn().isBlank() ? "102" : item.cstOuCsosn();

            linhas.add(new LinhaCalc(prod, vProd, vDesc, origem, csosn));
            first = false;
        }
        return linhas;
    }

    private Imposto montarImpostoSimples(String origem, String csosn) {
        Imposto imposto = new Imposto();

        ICMS icms = new ICMS();
        ICMSSN102 sn = new ICMSSN102();
        sn.setOrig(origem);
        sn.setCSOSN(csosn);
        icms.setICMSSN102(sn);

        PIS pis = new PIS();
        PISOutr pisOutr = new PISOutr();
        pisOutr.setCST("49");
        pisOutr.setVBC("0.00");
        pisOutr.setPPIS("0.00");
        pisOutr.setVPIS("0.00");
        pis.setPISOutr(pisOutr);

        COFINS cofins = new COFINS();
        COFINSOutr cofinsOutr = new COFINSOutr();
        cofinsOutr.setCST("49");
        cofinsOutr.setVBC("0.00");
        cofinsOutr.setPCOFINS("0.00");
        cofinsOutr.setVCOFINS("0.00");
        cofins.setCOFINSOutr(cofinsOutr);

        imposto.getContent().add(new JAXBElement<>(new QName("ICMS"), ICMS.class, icms));
        imposto.getContent().add(new JAXBElement<>(new QName("PIS"), PIS.class, pis));
        imposto.getContent().add(new JAXBElement<>(new QName("COFINS"), COFINS.class, cofins));
        return imposto;
    }

    private Total montarTotal(BigDecimal vProd, BigDecimal vDesc, BigDecimal vFrete, BigDecimal vNf) {
        Total total = new Total();
        ICMSTot t = new ICMSTot();
        t.setVBC("0.00");
        t.setVICMS("0.00");
        t.setVICMSDeson("0.00");
        t.setVFCP("0.00");
        t.setVFCPST("0.00");
        t.setVFCPSTRet("0.00");
        t.setVBCST("0.00");
        t.setVST("0.00");
        t.setVProd(vProd.setScale(2, RoundingMode.HALF_UP).toPlainString());
        t.setVFrete(vFrete.setScale(2, RoundingMode.HALF_UP).toPlainString());
        t.setVSeg("0.00");
        t.setVDesc(vDesc.setScale(2, RoundingMode.HALF_UP).toPlainString());
        t.setVII("0.00");
        t.setVIPI("0.00");
        t.setVIPIDevol("0.00");
        t.setVPIS("0.00");
        t.setVCOFINS("0.00");
        t.setVOutro("0.00");
        t.setVNF(vNf.toPlainString());
        total.setICMSTot(t);
        return total;
    }

    private Transp montarTransporte() {
        Transp transp = new Transp();
        transp.setModFrete("9");
        return transp;
    }

    private Pag montarPagamento(EmitirNotaRequest request, BigDecimal vNf) {
        Pag pag = new Pag();
        BigDecimal somaPag = BigDecimal.ZERO;
        for (EmitirNotaRequest.PagamentoRequest p : request.pagamentos()) {
            Pag.DetPag det = new Pag.DetPag();
            String tPag = normalizarTPag(p.forma());
            det.setTPag(tPag);
            det.setVPag(p.valor().setScale(2, RoundingMode.HALF_UP).toPlainString());
            // SEFAZ-GO: tPag 17 (PIX) exige grupo card.
            // tpIntegra=2 = não integrado à automação (PIX avulso no PDV) — sem CNPJ/bandeira/autorização.
            if ("17".equals(tPag)) {
                Pag.DetPag.Card card = new Pag.DetPag.Card();
                card.setTpIntegra("2");
                det.setCard(card);
            }
            if ("99".equals(tPag)) {
                String xPag = p.descricao();
                if (xPag == null || xPag.isBlank()) {
                    xPag = "Outros";
                }
                det.setXPag(xPag.length() > 60 ? xPag.substring(0, 60) : xPag);
            }
            pag.getDetPag().add(det);
            somaPag = somaPag.add(p.valor());
        }
        if (somaPag.setScale(2, RoundingMode.HALF_UP).compareTo(vNf) != 0) {
            throw new FiscalException(
                    "Soma dos pagamentos (" + somaPag + ") difere do total da nota (" + vNf + ")");
        }
        return pag;
    }

    private String normalizarTPag(String forma) {
        String f = forma.trim();
        return switch (f.toUpperCase()) {
            case "DINHEIRO", "01" -> "01";
            case "CHEQUE", "02" -> "02";
            case "CREDITO", "CARTAO_CREDITO", "03" -> "03";
            case "DEBITO", "CARTAO_DEBITO", "04" -> "04";
            case "PIX", "17" -> "17";
            case "OUTROS", "99" -> "99";
            default -> f.length() == 2 ? f : "99";
        };
    }

    private void validarTotais(EmitirNotaRequest request) {
        if (request.itens() == null || request.itens().isEmpty()) {
            throw new FiscalException("Informe ao menos um item");
        }
        if (request.pagamentos() == null || request.pagamentos().isEmpty()) {
            throw new FiscalException("Informe ao menos um pagamento");
        }
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
