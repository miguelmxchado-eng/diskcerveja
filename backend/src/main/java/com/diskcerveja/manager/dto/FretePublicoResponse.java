package com.diskcerveja.manager.dto;

import java.math.BigDecimal;

/** Cotação pública de frete por CEP. */
public record FretePublicoResponse(
        boolean coberta,
        BigDecimal taxa,
        String zona,
        BigDecimal pedidoMinimo,
        String mensagem) {}
