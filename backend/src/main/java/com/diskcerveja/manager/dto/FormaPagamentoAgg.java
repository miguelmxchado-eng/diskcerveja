package com.diskcerveja.manager.dto;

import com.diskcerveja.manager.domain.enums.FormaPagamento;
import java.math.BigDecimal;

public class FormaPagamentoAgg {

    private final FormaPagamento forma;
    private final BigDecimal total;

    public FormaPagamentoAgg(FormaPagamento forma, BigDecimal total) {
        this.forma = forma;
        this.total = total != null ? total : BigDecimal.ZERO;
    }

    public FormaPagamento getForma() {
        return forma;
    }

    public BigDecimal getTotal() {
        return total;
    }
}
