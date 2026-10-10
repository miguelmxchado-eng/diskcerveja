package com.diskcerveja.manager.dto;

import java.math.BigDecimal;

/**
 * Item do ranking de mais vendidos. {@code chave} identifica o item para buscar a série diária
 * (ex.: {@code p12u} = produto 12 vendido por unidade, {@code c3} = combo 3).
 */
public record MaisVendidoItemDto(
        String chave,
        Long produtoId,
        Long comboId,
        boolean vendaUnidade,
        String nome,
        String categoria,
        long unidades,
        BigDecimal valor,
        BigDecimal lucro,
        long pedidos,
        long unidadesAnterior,
        BigDecimal valorAnterior) {}
