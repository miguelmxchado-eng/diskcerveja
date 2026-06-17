package com.diskcerveja.manager.dto;

import jakarta.validation.constraints.Positive;

/** Item de pedido: informe {@code produtoId} OU {@code comboId} (exatamente um). */
public record PedidoItemRequest(Long produtoId, Long comboId, @Positive int quantidade) {}
