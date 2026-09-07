package com.diskcerveja.manager.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ContaClienteRegistrarRequest(
        @NotBlank @Size(max = 120) String nome,
        @NotBlank @Size(max = 32) String telefone,
        @NotBlank @Size(min = 4, max = 40) String senha,
        /** Obrigatório se esse WhatsApp já fez pedido sem conta. */
        Long pedidoId,
        @Size(max = 9) String cep,
        @Size(max = 200) String logradouro,
        @Size(max = 30) String numero,
        @Size(max = 120) String complemento,
        @Size(max = 120) String bairro,
        @Size(max = 120) String cidade,
        @Size(max = 2) String uf) {}
