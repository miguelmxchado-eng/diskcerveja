package com.diskcerveja.manager.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ComboOpcaoDto(
        Long id,
        @NotBlank @Size(max = 120) String rotulo,
        Long produtoId,
        int ordem,
        boolean ativo) {}
