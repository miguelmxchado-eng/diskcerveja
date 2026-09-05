package com.diskcerveja.manager.dto;

import java.math.BigDecimal;

public record PedidoPeriodoPagamentoDto(String forma, BigDecimal valor, int percentual) {}
