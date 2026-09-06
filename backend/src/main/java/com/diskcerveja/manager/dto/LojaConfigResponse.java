package com.diskcerveja.manager.dto;

import java.math.BigDecimal;

public record LojaConfigResponse(
        String nome,
        String whatsapp,
        boolean aberta,
        String horario,
        BigDecimal taxaEntrega,
        BigDecimal pedidoMinimo,
        String info,
        String infinitepayHandle,
        String publicBaseUrl) {}
