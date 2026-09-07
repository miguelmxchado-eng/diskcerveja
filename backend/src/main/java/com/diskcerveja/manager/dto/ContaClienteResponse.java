package com.diskcerveja.manager.dto;

public record ContaClienteResponse(
        String token,
        Long clienteId,
        String nome,
        String telefone,
        String cep,
        String logradouro,
        String numero,
        String complemento,
        String bairro,
        String cidade,
        String uf) {}
