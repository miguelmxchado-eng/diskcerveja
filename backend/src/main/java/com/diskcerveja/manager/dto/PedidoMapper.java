package com.diskcerveja.manager.dto;

import com.diskcerveja.manager.domain.entity.Pedido;
import com.diskcerveja.manager.domain.entity.PedidoItem;
import java.util.List;

public final class PedidoMapper {

    private PedidoMapper() {}

    public static PedidoResponse toResponse(Pedido p) {
        List<PedidoItemResponse> itens = p.getItens().stream()
                .map(PedidoMapper::item)
                .toList();
        return new PedidoResponse(
                p.getId(),
                p.getDataHora(),
                p.getClienteNome(),
                p.getTelefone(),
                p.getTipo(),
                p.getStatus(),
                p.getTotal(),
                p.getFormaPagamento(),
                p.getEnderecoEntrega(),
                p.isEstoqueBaixado(),
                itens);
    }

    private static PedidoItemResponse item(PedidoItem i) {
        if (i.isCombo()) {
            String nome = i.getDescricao() != null ? i.getDescricao() : i.getCombo().getNome();
            return new PedidoItemResponse(
                    null,
                    i.getCombo().getId(),
                    nome,
                    i.getQuantidade(),
                    i.getPrecoUnitario(),
                    i.getCustoUnitario());
        }
        return new PedidoItemResponse(
                i.getProduto().getId(),
                null,
                i.getProduto().getNome(),
                i.getQuantidade(),
                i.getPrecoUnitario(),
                i.getCustoUnitario());
    }
}
