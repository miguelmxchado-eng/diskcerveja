package com.diskcerveja.manager.dto;

import com.diskcerveja.manager.domain.enums.FormaPagamento;
import com.diskcerveja.manager.domain.enums.StatusPedido;
import java.math.BigDecimal;
import java.time.Instant;

public record PedidoPublicoResponse(
        Long id,
        Instant dataHora,
        StatusPedido status,
        BigDecimal total,
        BigDecimal taxaEntrega,
        FormaPagamento formaPagamento,
        String mensagem,
        String checkoutUrl,
        boolean pagamentoOnline) {}
