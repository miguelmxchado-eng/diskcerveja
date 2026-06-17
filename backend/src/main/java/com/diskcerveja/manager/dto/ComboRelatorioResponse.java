package com.diskcerveja.manager.dto;

import java.math.BigDecimal;

public record ComboRelatorioResponse(
        Long comboId,
        String nome,
        long quantidadeVendida,
        BigDecimal faturamento,
        BigDecimal custoTotal,
        BigDecimal lucro,
        BigDecimal margem) {}
