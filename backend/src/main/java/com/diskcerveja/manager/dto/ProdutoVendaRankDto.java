package com.diskcerveja.manager.dto;

import java.math.BigDecimal;

public record ProdutoVendaRankDto(
        Long produtoId,
        String nome,
        long unidades,
        BigDecimal valor,
        int estoqueAtual,
        int estoqueMinimo,
        boolean estoqueBaixo) {}
