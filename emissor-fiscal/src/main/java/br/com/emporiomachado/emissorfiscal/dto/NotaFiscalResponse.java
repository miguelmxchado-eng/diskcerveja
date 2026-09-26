package br.com.emporiomachado.emissorfiscal.dto;

import br.com.emporiomachado.emissorfiscal.domain.NotaFiscal;
import br.com.emporiomachado.emissorfiscal.domain.NotaStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record NotaFiscalResponse(
        UUID id,
        Integer serie,
        Long numero,
        String chaveAcesso,
        NotaStatus status,
        String cStat,
        String xMotivo,
        String protocolo,
        BigDecimal valorTotal,
        String pedidoExternoId,
        String qrCode,
        Instant criadoEm,
        Instant atualizadoEm
) {
    public static NotaFiscalResponse from(NotaFiscal n) {
        return new NotaFiscalResponse(
                n.getId(),
                n.getSerie(),
                n.getNumero(),
                n.getChaveAcesso(),
                n.getStatus(),
                n.getCStat(),
                n.getXMotivo(),
                n.getProtocolo(),
                n.getValorTotal(),
                n.getPedidoExternoId(),
                n.getQrCode(),
                n.getCriadoEm(),
                n.getAtualizadoEm()
        );
    }
}
