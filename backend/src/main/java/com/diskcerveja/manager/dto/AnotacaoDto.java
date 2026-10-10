package com.diskcerveja.manager.dto;

import jakarta.validation.constraints.Size;
import java.time.Instant;

public record AnotacaoDto(
        Long id,
        @Size(max = 120, message = "Título muito longo (máx. 120 caracteres).") String titulo,
        @Size(max = 4000, message = "Texto muito longo (máx. 4000 caracteres).") String texto,
        String cor,
        Boolean fixada,
        Boolean concluida,
        String autorNome,
        Instant criadoEm,
        Instant atualizadoEm) {}
