package com.diskcerveja.manager.dto;

import jakarta.validation.constraints.Size;

public record ContaClienteAtualizarRequest(
        @Size(max = 120) String nome,
        @Size(max = 9) String cep,
        @Size(max = 200) String logradouro,
        @Size(max = 30) String numero,
        @Size(max = 120) String complemento,
        @Size(max = 120) String bairro,
        @Size(max = 120) String cidade,
        @Size(max = 2) String uf) {}
