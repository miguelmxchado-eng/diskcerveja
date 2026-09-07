package com.diskcerveja.manager.dto;

import com.diskcerveja.manager.domain.enums.FormaPagamento;
import com.diskcerveja.manager.domain.enums.TipoPedido;
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
        /** Obrigatório na entrega; na retirada o backend grava "Retirada na loja". */
        @Size(max = 400) String enderecoEntrega,
        /** Obrigatório na entrega. */
        @Size(max = 9) String cep,
        @Size(max = 120) String bairro,
        @Size(max = 200) String logradouro,
        @Size(max = 30) String numero,
        @Size(max = 120) String complemento,
        @Size(max = 120) String cidade,
        @Size(max = 2) String uf,
        /** ENTREGA (padrão) ou RETIRADA. */
        TipoPedido tipo,
        /** Opcional: a forma final vem do checkout InfinitePay (Pix/cartão). */
        FormaPagamento formaPagamento,
        @Size(max = 240) String observacao,
        @NotEmpty @Valid List<Item> itens) {

    public record Item(
            @NotBlank String tipo,
            @NotNull Long id,
            @Positive int quantidade,
            Boolean vendaUnidade) {}
}
