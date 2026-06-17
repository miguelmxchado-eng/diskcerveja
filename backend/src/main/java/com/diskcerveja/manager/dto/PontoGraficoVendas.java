package com.diskcerveja.manager.dto;

import java.math.BigDecimal;

/**
 * Um ponto do gráfico de vendas vs cancelamentos (valores reais do período).
 */
public record PontoGraficoVendas(
        String rotulo,
        String rotuloCompleto,
        BigDecimal vendas,
        long cancelamentos) {}
