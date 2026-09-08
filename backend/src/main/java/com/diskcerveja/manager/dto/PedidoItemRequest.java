package com.diskcerveja.manager.dto;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Item de pedido: informe {@code produtoId} OU {@code comboId} (exatamente um). */
public record PedidoItemRequest(
        Long produtoId,
        Long comboId,
        @Positive int quantidade,
        /** true = unidade avulsa; null/false = pacote/embalagem. */
        Boolean vendaUnidade,
        /** Escolhas do cliente (copão) ou observação do item. */
        @Size(max = 500) String observacao) {

    public PedidoItemRequest(Long produtoId, Long comboId, int quantidade, Boolean vendaUnidade) {
        this(produtoId, comboId, quantidade, vendaUnidade, null);
    }
}
