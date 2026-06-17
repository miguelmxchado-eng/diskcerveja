package com.diskcerveja.manager.dto;

import com.diskcerveja.manager.domain.enums.FormaPagamento;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record DashboardResponse(
        BigDecimal vendasHoje,
        BigDecimal vendasOntem,
        long cancelamentosHoje,
        long pedidosEmAndamento,
        long pedidosAtrasados,
        long produtosBaixoEstoque,
        ResumoCaixaHoje caixa,
        List<PontoGraficoVendas> graficoDiario,
        List<PontoGraficoVendas> graficoSemanal,
        List<PontoGraficoVendas> graficoMensal) {

    public record ResumoCaixaHoje(
            boolean caixaAberto,
            BigDecimal valorAbertura,
            BigDecimal saldoPrevisto,
            Map<FormaPagamento, BigDecimal> vendasPorFormaPagamento) {}
}
