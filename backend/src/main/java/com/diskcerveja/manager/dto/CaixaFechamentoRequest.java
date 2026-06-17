package com.diskcerveja.manager.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record CaixaFechamentoRequest(@NotNull BigDecimal valorFechamento) {}
