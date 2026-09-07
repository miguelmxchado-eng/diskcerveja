package com.diskcerveja.manager.dto;

import com.diskcerveja.manager.domain.enums.FormaPagamento;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record PedidoPagamentoRequest(
        @NotNull FormaPagamento formaPagamento,
        @NotNull @DecimalMin(value = "0.01", message = "Valor do pagamento deve ser maior que zero")
                BigDecimal valor,
        @DecimalMin(value = "0.01", message = "Valor recebido deve ser maior que zero")
                BigDecimal valorRecebido) {}
