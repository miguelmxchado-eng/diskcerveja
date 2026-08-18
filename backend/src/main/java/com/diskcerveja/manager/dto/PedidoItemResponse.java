package com.diskcerveja.manager.dto;

import java.math.BigDecimal;

public record PedidoItemResponse(
        Long produtoId,
        Long comboId,
        String produtoNome,
        int quantidade,
        BigDecimal precoUnitario,
        BigDecimal custoUnitario) {}
