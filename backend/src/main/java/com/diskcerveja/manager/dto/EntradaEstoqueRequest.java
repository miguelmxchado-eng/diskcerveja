package com.diskcerveja.manager.dto;

import jakarta.validation.constraints.Positive;

public record EntradaEstoqueRequest(@Positive int quantidade, String motivo) {}
