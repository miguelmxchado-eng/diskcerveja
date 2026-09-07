package com.diskcerveja.manager.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record ZonaEntregaDto(
        Long id,
        @NotBlank @Size(max = 80) String nome,
        @NotNull @DecimalMin("0.00") BigDecimal taxa,
        @NotBlank @Size(max = 500) String cepPrefixos,
        boolean ativo,
        int ordem) {}
