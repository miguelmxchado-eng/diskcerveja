package com.diskcerveja.manager.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ClienteDto(
        Long id,
        @NotBlank @Size(max = 255) String nome,
        @Size(max = 40) String telefone,
        @Size(max = 500) String endereco,
        @Size(max = 500) String observacao,
        Boolean ativo) {}
