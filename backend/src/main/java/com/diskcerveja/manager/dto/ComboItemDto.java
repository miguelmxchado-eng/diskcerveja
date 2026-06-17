package com.diskcerveja.manager.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ComboItemDto(@NotNull Long produtoId, @Positive int quantidade) {}
