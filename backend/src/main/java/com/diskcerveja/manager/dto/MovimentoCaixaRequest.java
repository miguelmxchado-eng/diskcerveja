package com.diskcerveja.manager.dto;

import com.diskcerveja.manager.domain.enums.TipoMovimentoCaixa;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record MovimentoCaixaRequest(@NotNull TipoMovimentoCaixa tipo, @NotNull @Positive BigDecimal valor, String descricao) {}
