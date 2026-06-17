package com.diskcerveja.manager.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record CaixaAberturaRequest(@NotNull BigDecimal valorAbertura) {}
