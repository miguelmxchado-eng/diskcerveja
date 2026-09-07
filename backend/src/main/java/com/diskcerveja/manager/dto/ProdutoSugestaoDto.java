package com.diskcerveja.manager.dto;

import java.math.BigDecimal;

public record ProdutoSugestaoDto(
        Long produtoId, String nome, BigDecimal preco, long vezesJunto, int estoqueAtual) {}
