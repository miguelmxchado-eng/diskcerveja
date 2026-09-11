package com.diskcerveja.manager.dto;

import com.diskcerveja.manager.domain.enums.FormaPagamento;
import com.diskcerveja.manager.domain.enums.StatusContaFinanceira;
import com.diskcerveja.manager.domain.enums.TipoContaFinanceira;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record ContaFinanceiraDto(
        Long id,
        @NotNull TipoContaFinanceira tipo,
        @NotBlank @Size(max = 200) String descricao,
        @Size(max = 120) String pessoa,
        @NotNull @DecimalMin("0.01") BigDecimal valor,
        @NotNull LocalDate vencimento,
        StatusContaFinanceira status,
        LocalDate dataPagamento,
        FormaPagamento formaPagamento,
        @Size(max = 500) String observacao) {}
