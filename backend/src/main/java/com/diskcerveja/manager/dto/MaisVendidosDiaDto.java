package com.diskcerveja.manager.dto;

import java.math.BigDecimal;

/** {@code dia} em ISO (yyyy-MM-dd), dia de operação em America/Sao_Paulo. */
public record MaisVendidosDiaDto(String dia, long unidades, BigDecimal valor) {}
