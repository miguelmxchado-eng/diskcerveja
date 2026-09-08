package com.diskcerveja.manager.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ComboOpcaoGrupoDto(
        Long id,
        @NotBlank @Size(max = 120) String nome,
        boolean obrigatorio,
        @Min(0) int minimo,
        @Min(1) int maximo,
        int ordem,
        @NotNull @Valid List<ComboOpcaoDto> opcoes) {}
