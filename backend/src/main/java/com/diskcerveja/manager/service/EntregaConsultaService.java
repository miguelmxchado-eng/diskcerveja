package com.diskcerveja.manager.service;

import com.diskcerveja.manager.domain.entity.Entrega;
import com.diskcerveja.manager.domain.entity.Pedido;
import com.diskcerveja.manager.domain.enums.StatusEntrega;
import com.diskcerveja.manager.domain.enums.StatusPedido;
import com.diskcerveja.manager.domain.enums.TipoPedido;
import com.diskcerveja.manager.dto.EntregaResumoResponse;
import com.diskcerveja.manager.repository.PedidoRepository;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EntregaConsultaService {

    private final PedidoRepository pedidoRepository;

    public EntregaConsultaService(PedidoRepository pedidoRepository) {
        this.pedidoRepository = pedidoRepository;
    }

    @Transactional(readOnly = true)
    public List<EntregaResumoResponse> listarPainel() {
        return pedidoRepository
                .findEntregaPainel(
                        TipoPedido.ENTREGA,
                        StatusPedido.ABERTO,
                        StatusPedido.EM_PREPARO,
                        StatusPedido.SAIU_ENTREGA)
                .stream()
                .map(EntregaConsultaService::toResumo)
                .toList();
    }

    private static EntregaResumoResponse toResumo(Pedido p) {
        Entrega e = p.getEntrega();
        if (e != null) {
            return new EntregaResumoResponse(
                    e.getId(),
                    p.getId(),
                    p.getClienteNome(),
                    p.getTelefone(),
                    p.getEnderecoEntrega(),
                    e.getTaxaEntrega(),
                    e.getStatus(),
                    p.getStatus(),
                    e.getEntregadorNome());
        }
        return new EntregaResumoResponse(
                null,
                p.getId(),
                p.getClienteNome(),
                p.getTelefone(),
                p.getEnderecoEntrega(),
                BigDecimal.ZERO,
                StatusEntrega.PENDENTE,
                p.getStatus(),
                null);
    }
}
