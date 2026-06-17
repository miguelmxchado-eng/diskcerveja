package com.diskcerveja.manager.dto;

import com.diskcerveja.manager.domain.enums.FormaPagamento;
import com.diskcerveja.manager.domain.enums.TipoPedido;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.List;

public record PedidoUpdateRequest(
        String clienteNome,
        String telefone,
        TipoPedido tipo,
        FormaPagamento formaPagamento,
        String enderecoEntrega,
        BigDecimal taxaEntrega,
        String entregadorNome,
        @Valid List<PedidoItemRequest> itens) {}
