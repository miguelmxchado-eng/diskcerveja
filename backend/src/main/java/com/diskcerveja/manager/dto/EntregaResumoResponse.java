package com.diskcerveja.manager.dto;

import com.diskcerveja.manager.domain.enums.StatusEntrega;
import com.diskcerveja.manager.domain.enums.StatusPedido;
import java.math.BigDecimal;

public record EntregaResumoResponse(
        Long entregaId,
        Long pedidoId,
        String clienteNome,
        String telefone,
        String enderecoEntrega,
        BigDecimal taxaEntrega,
        StatusEntrega statusEntrega,
        StatusPedido statusPedido,
        String entregadorNome) {}
