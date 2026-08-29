package com.diskcerveja.manager.dto;

import com.diskcerveja.manager.domain.enums.FormaPagamento;
import com.diskcerveja.manager.domain.enums.StatusPedido;
import com.diskcerveja.manager.domain.enums.TipoPedido;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record PedidoResponse(
        Long id,
        Instant dataHora,
        String clienteNome,
        String telefone,
        TipoPedido tipo,
        StatusPedido status,
        BigDecimal total,
        BigDecimal desconto,
        FormaPagamento formaPagamento,
        String enderecoEntrega,
        boolean estoqueBaixado,
        List<PedidoItemResponse> itens) {}
