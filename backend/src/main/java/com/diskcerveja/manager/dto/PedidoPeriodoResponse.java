package com.diskcerveja.manager.dto;

import com.diskcerveja.manager.domain.enums.PeriodoPedido;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record PedidoPeriodoResponse(
        PeriodoPedido periodo,
        String periodoDescricao,
        @JsonFormat(pattern = "yyyy-MM-dd") LocalDate dataInicio,
        @JsonFormat(pattern = "yyyy-MM-dd") LocalDate dataFim,
        long quantidadeDiasNoPeriodo,
        List<PedidoResumoDto> pedidos,
        BigDecimal somaTotalPedidos,
        BigDecimal somaVendasEntregues,
        BigDecimal somaCustoEntregues,
        BigDecimal somaLucroEntregues,
        BigDecimal margemPercentual,
        int quantidadeEntreguesSemCaixa) {}
