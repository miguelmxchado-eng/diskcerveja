package com.diskcerveja.manager.service;

import com.diskcerveja.manager.domain.entity.Entrega;
import com.diskcerveja.manager.domain.entity.Pedido;
import com.diskcerveja.manager.domain.enums.FormaPagamento;
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
                // Pedido do cardápio (sem operador) só entra no painel depois do pagamento online.
                .filter(p -> p.getUsuario() != null || p.isPagamentoConfirmado())
                .map(EntregaConsultaService::toResumo)
                .toList();
    }

    private static EntregaResumoResponse toResumo(Pedido p) {
        Entrega e = p.getEntrega();
        BigDecimal taxa = e != null && e.getTaxaEntrega() != null ? e.getTaxaEntrega() : BigDecimal.ZERO;
        StatusEntrega statusEntrega = e != null ? e.getStatus() : StatusEntrega.PENDENTE;
        String entregadorNome = e != null ? e.getEntregadorNome() : null;
        Long entregaId = e != null ? e.getId() : null;
        return new EntregaResumoResponse(
                entregaId,
                p.getId(),
                p.getClienteNome(),
                p.getTelefone(),
                p.getEnderecoEntrega(),
                taxa,
                p.getTotal() != null ? p.getTotal() : BigDecimal.ZERO,
                p.getFormaPagamento() != null ? p.getFormaPagamento() : FormaPagamento.PIX,
                p.isPagamentoConfirmado(),
                statusEntrega,
                p.getStatus(),
                entregadorNome);
    }
}
