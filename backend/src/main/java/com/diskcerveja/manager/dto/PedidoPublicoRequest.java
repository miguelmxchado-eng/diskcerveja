package com.diskcerveja.manager.dto;

import com.diskcerveja.manager.domain.enums.FormaPagamento;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

public record PedidoPublicoRequest(
        @NotBlank @Size(max = 120) String clienteNome,
        @NotBlank @Size(max = 32) String telefone,
        @NotBlank @Size(max = 400) String enderecoEntrega,
        @NotBlank @Size(max = 9) String cep,
        @Size(max = 120) String bairro,
        @NotNull FormaPagamento formaPagamento,
        @Size(max = 240) String observacao,
        @NotEmpty @Valid List<Item> itens) {

    public record Item(
            @NotBlank String tipo,
            @NotNull Long id,
            @Positive int quantidade,
            Boolean vendaUnidade) {}
}
