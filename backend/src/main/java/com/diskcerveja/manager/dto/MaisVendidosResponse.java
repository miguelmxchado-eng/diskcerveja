package com.diskcerveja.manager.dto;

import java.math.BigDecimal;
import java.util.List;

public record MaisVendidosResponse(
        String inicio,
        String fim,
        String inicioAnterior,
        String fimAnterior,
        long pedidos,
        long totalUnidades,
        BigDecimal totalValor,
        BigDecimal totalLucro,
        long totalUnidadesAnterior,
        BigDecimal totalValorAnterior,
        List<MaisVendidoItemDto> itens,
        List<MaisVendidosCategoriaDto> categorias,
        List<MaisVendidosDiaDto> porDia) {}
