package br.com.emporiomachado.emissorfiscal.service;

import br.com.emporiomachado.emissorfiscal.config.FiscalProperties;
import br.com.emporiomachado.emissorfiscal.config.NfeConfigFactory;
import br.com.emporiomachado.emissorfiscal.domain.NotaFiscal;
import br.com.emporiomachado.emissorfiscal.domain.NotaFiscalRepository;
import br.com.emporiomachado.emissorfiscal.domain.NotaStatus;
import br.com.emporiomachado.emissorfiscal.exception.FiscalException;
import br.com.emporiomachado.emissorfiscal.exception.NotaNaoEncontradaException;
import br.com.swconsultoria.nfe.Nfe;
import br.com.swconsultoria.nfe.dom.ConfiguracoesNfe;
import br.com.swconsultoria.nfe.dom.Evento;
import br.com.swconsultoria.nfe.dom.enuns.DocumentoEnum;
import br.com.swconsultoria.nfe.schemas_eventos.TEnvEventoCancelamento;
import br.com.swconsultoria.nfe.schemas_eventos.TRetEnvEventoCancelamento;
import br.com.swconsultoria.nfe.util.CancelamentoUtil;
import br.com.swconsultoria.nfe.util.RetornoUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class CancelamentoService {

    private static final Logger log = LoggerFactory.getLogger(CancelamentoService.class);
    private static final ZoneId SP = ZoneId.of("America/Sao_Paulo");
    /** Prazo legal comum: 30 minutos após autorização (pode variar por UF/NT). */
    private static final long PRAZO_MINUTOS = 30;

    private final NotaFiscalRepository repository;
    private final NfeConfigFactory configFactory;
    private final FiscalProperties properties;

    public CancelamentoService(
            NotaFiscalRepository repository,
            NfeConfigFactory configFactory,
            FiscalProperties properties
    ) {
        this.repository = repository;
        this.configFactory = configFactory;
        this.properties = properties;
    }

    @Transactional
    public NotaFiscal cancelar(UUID id, String justificativa) {
        NotaFiscal nota = repository.findById(id)
                .orElseThrow(() -> new NotaNaoEncontradaException(id));

        if (nota.getStatus() != NotaStatus.AUTORIZADA) {
            throw new FiscalException("Somente notas AUTORIZADAS podem ser canceladas (status=" + nota.getStatus() + ")");
        }
        if (nota.getProtocolo() == null || nota.getChaveAcesso() == null) {
            throw new FiscalException("Nota sem protocolo/chave — não é possível cancelar");
        }
        if (justificativa == null || justificativa.trim().length() < 15) {
            throw new FiscalException("Justificativa deve ter no mínimo 15 caracteres");
        }

        Instant emitido = nota.getCriadoEm() != null ? nota.getCriadoEm() : Instant.now();
        long minutos = ChronoUnit.MINUTES.between(emitido, Instant.now());
        if (minutos > PRAZO_MINUTOS) {
            // Stub de regra: registra aviso; SEFAZ ainda pode rejeitar. Ajuste conforme calendário GO.
            log.warn("Cancelamento fora do prazo típico de {} min ({} min). Tentando mesmo assim.",
                    PRAZO_MINUTOS, minutos);
        }

        try {
            ConfiguracoesNfe config = configFactory.criar();

            Evento cancela = new Evento();
            cancela.setChave(nota.getChaveAcesso());
            cancela.setProtocolo(nota.getProtocolo());
            cancela.setCnpj(properties.emitente().cnpjDigits());
            cancela.setMotivo(justificativa.trim());
            cancela.setDataEvento(LocalDateTime.now(SP));

            TEnvEventoCancelamento enviEvento = CancelamentoUtil.montaCancelamento(cancela, config);
            TRetEnvEventoCancelamento retorno = Nfe.cancelarNfe(config, enviEvento, true, DocumentoEnum.NFCE);
            RetornoUtil.validaCancelamento(retorno);

            String proc = CancelamentoUtil.criaProcEventoCancelamento(
                    config, enviEvento, retorno.getRetEvento().get(0));
            nota.setXmlCancelamento(proc);
            if (!retorno.getRetEvento().isEmpty()) {
                nota.setCStat(retorno.getRetEvento().get(0).getInfEvento().getCStat());
                nota.setXMotivo(retorno.getRetEvento().get(0).getInfEvento().getXMotivo());
            }
            nota.setStatus(NotaStatus.CANCELADA);
            return repository.save(nota);
        } catch (FiscalException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new FiscalException("Falha no cancelamento: " + ex.getMessage(), ex);
        }
    }
}
