package com.diskcerveja.manager.dto;

import com.diskcerveja.manager.domain.enums.StatusCaixaSessao;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record CaixaSessaoResponse(
        Long id,
        LocalDate dataReferencia,
        StatusCaixaSessao status,
        Instant horaAbertura,
        Instant horaFechamento,
        BigDecimal valorAbertura,
        BigDecimal valorFechamento,
        BigDecimal saldoPrevisto,
        List<MovimentoCaixaResponse> movimentosRecentes) {}
