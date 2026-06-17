package com.diskcerveja.manager.dto;

import com.diskcerveja.manager.domain.enums.TipoMovimentoCaixa;
import java.math.BigDecimal;
import java.time.Instant;

public record MovimentoCaixaResponse(Long id, TipoMovimentoCaixa tipo, BigDecimal valor, String descricao, Instant createdAt) {}
