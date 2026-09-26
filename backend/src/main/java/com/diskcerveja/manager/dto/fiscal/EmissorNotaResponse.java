package com.diskcerveja.manager.dto.fiscal;

import java.math.BigDecimal;

public record EmissorNotaResponse(
        String id,
        Integer serie,
        Long numero,
        String chaveAcesso,
        String status,
        String cStat,
        String xMotivo,
        String protocolo,
        BigDecimal valorTotal,
        String pedidoExternoId,
        String qrCode) {}
