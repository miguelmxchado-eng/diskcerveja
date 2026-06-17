package com.diskcerveja.manager.dto;

import com.diskcerveja.manager.domain.enums.FormaPagamento;
import com.diskcerveja.manager.domain.enums.TipoPedido;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

public record PedidoRequest(
        String clienteNome,
        String telefone,
        @NotNull TipoPedido tipo,
        @NotNull FormaPagamento formaPagamento,
        String enderecoEntrega,
        BigDecimal taxaEntrega,
        String entregadorNome,
        @NotEmpty @Valid List<PedidoItemRequest> itens) {}
