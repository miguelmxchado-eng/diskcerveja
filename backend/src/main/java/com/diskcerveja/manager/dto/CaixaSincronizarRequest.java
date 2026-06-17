package com.diskcerveja.manager.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record CaixaSincronizarRequest(@NotNull LocalDate inicio, @NotNull LocalDate fim) {}
