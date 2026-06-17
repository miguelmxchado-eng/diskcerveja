package com.diskcerveja.manager.dto;

import jakarta.validation.constraints.NotNull;

public record ConfigCaixaPatchRequest(@NotNull Boolean caixaObrigatorio) {}
