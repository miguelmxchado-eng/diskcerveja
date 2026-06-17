package com.diskcerveja.manager.dto;

import jakarta.validation.constraints.NotNull;

public record AjusteEstoqueRequest(@NotNull Integer novaQuantidade, String motivo) {}
