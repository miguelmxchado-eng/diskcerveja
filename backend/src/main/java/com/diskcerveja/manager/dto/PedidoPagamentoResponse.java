package com.diskcerveja.manager.dto;

import com.diskcerveja.manager.domain.enums.FormaPagamento;
import java.math.BigDecimal;

public record PedidoPagamentoResponse(
        FormaPagamento formaPagamento,
        BigDecimal valor,
        BigDecimal valorRecebido,
        BigDecimal troco) {}
