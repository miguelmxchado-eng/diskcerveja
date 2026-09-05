package com.diskcerveja.manager.dto;

import com.diskcerveja.manager.domain.enums.FormaPagamento;
import com.diskcerveja.manager.domain.enums.StatusPedido;
import com.diskcerveja.manager.domain.enums.TipoPedido;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record PedidoResumoDto(
        Long id,
        Instant dataHora,
        String clienteNome,
        String telefone,
        TipoPedido tipo,
        StatusPedido status,
        BigDecimal total,
        BigDecimal desconto,
        BigDecimal custo,
        BigDecimal lucro,
        FormaPagamento formaPagamento,
        boolean registradoNoCaixa,
        List<PedidoItemResponse> itens) {}
