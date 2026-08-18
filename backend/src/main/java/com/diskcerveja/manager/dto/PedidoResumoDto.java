package com.diskcerveja.manager.dto;

import com.diskcerveja.manager.domain.enums.FormaPagamento;
import com.diskcerveja.manager.domain.enums.StatusPedido;
import com.diskcerveja.manager.domain.enums.TipoPedido;
import java.math.BigDecimal;
import java.time.Instant;

public record PedidoResumoDto(
        Long id,
        Instant dataHora,
        String clienteNome,
        String telefone,
        TipoPedido tipo,
        StatusPedido status,
        BigDecimal total,
        BigDecimal custo,
        BigDecimal lucro,
        FormaPagamento formaPagamento,
        boolean registradoNoCaixa) {}
