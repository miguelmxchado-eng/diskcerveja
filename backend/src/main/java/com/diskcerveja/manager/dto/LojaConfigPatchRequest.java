package com.diskcerveja.manager.dto;

import java.math.BigDecimal;

public record LojaConfigPatchRequest(
        String nome,
        String whatsapp,
        Boolean aberta,
        String horario,
        BigDecimal taxaEntrega,
        BigDecimal pedidoMinimo,
        String info,
        String infinitepayHandle,
        String publicBaseUrl) {}
