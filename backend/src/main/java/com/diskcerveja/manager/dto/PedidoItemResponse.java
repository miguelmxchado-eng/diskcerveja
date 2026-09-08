package com.diskcerveja.manager.dto;

import java.math.BigDecimal;

public record PedidoItemResponse(
        Long produtoId,
        Long comboId,
        String produtoNome,
        int quantidade,
        BigDecimal precoUnitario,
        BigDecimal custoUnitario,
        String observacao) {

    public PedidoItemResponse(
            Long produtoId,
            Long comboId,
            String produtoNome,
            int quantidade,
            BigDecimal precoUnitario,
            BigDecimal custoUnitario) {
        this(produtoId, comboId, produtoNome, quantidade, precoUnitario, custoUnitario, null);
    }
}
