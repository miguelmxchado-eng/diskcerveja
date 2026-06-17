package com.diskcerveja.manager.dto;

import java.math.BigDecimal;

public record ComboItemResponse(
        Long produtoId,
        String produtoNome,
        int quantidade,
        BigDecimal custoUnitario,
        BigDecimal precoUnitario,
        int estoqueDisponivel,
        boolean produtoAtivo) {}
