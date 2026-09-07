package com.diskcerveja.manager.dto;

import java.math.BigDecimal;

/** Projeção de fechamento do mês com ritmo útil/fim de semana e faixas. */
public record ProjecaoMensalResponse(
        int diasDecorridos,
        int diasRestantes,
        int diasNoMes,
        int progressoMes,
        BigDecimal faturamentoAtual,
        BigDecimal lucroAtual,
        long pedidosAtual,
        BigDecimal mediaDiaria,
        BigDecimal mediaDiaUtil,
        BigDecimal mediaFimSemana,
        BigDecimal estimativaRestante,
        BigDecimal projetadoPessimista,
        BigDecimal projetadoRealista,
        BigDecimal projetadoOtimista,
        BigDecimal lucroProjetado,
        long pedidosProjetados,
        BigDecimal metaMensal,
        String metaOrigem,
        BigDecimal faltaParaMeta,
        BigDecimal faltaPorDia,
        boolean noRitmoDaMeta,
        BigDecimal mesmoMesAnoPassado,
        BigDecimal mesmoPeriodoAnoPassado,
        BigDecimal mesAnterior) {}
