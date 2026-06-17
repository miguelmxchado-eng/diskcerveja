package com.diskcerveja.manager.dto;

import com.diskcerveja.manager.domain.enums.StatusPedido;
import jakarta.validation.constraints.NotNull;

public record StatusPedidoPatchRequest(@NotNull StatusPedido status) {}
