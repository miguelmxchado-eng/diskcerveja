package com.diskcerveja.manager.dto;

import java.math.BigDecimal;

/** Agregação de vendas de combos (linhas de pedido entregues). */
public record ComboVendaAgg(Long comboId, long quantidade, BigDecimal faturamento) {}
