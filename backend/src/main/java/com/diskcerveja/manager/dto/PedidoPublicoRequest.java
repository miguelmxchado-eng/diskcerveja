package com.diskcerveja.manager.dto;

import com.diskcerveja.manager.domain.enums.FormaPagamento;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;

public record PedidoPublicoRequest(
        @NotBlank String clienteNome,
        @NotBlank String telefone,
        @NotBlank String enderecoEntrega,
        @NotNull FormaPagamento formaPagamento,
        String observacao,
        @NotEmpty @Valid List<Item> itens) {

    public record Item(
            @NotBlank String tipo,
            @NotNull Long id,
            @Positive int quantidade,
            Boolean vendaUnidade) {}
}
