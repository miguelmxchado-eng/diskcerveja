package com.diskcerveja.manager.dto;

import com.diskcerveja.manager.domain.entity.Pedido;
import com.diskcerveja.manager.domain.entity.PedidoItem;
import java.math.BigDecimal;
import java.util.List;

public final class PedidoMapper {

    private PedidoMapper() {}

    public static PedidoResponse toResponse(Pedido p) {
        List<PedidoItemResponse> itens = p.getItens().stream()
                .map(PedidoMapper::item)
                .toList();
        List<PedidoPagamentoResponse> pagamentos = p.getPagamentos().stream()
                .map(pg -> {
                    BigDecimal recebido = pg.getValorRecebido();
                    BigDecimal troco = recebido != null
                            ? recebido.subtract(pg.getValor()).max(BigDecimal.ZERO)
                            : BigDecimal.ZERO;
                    return new PedidoPagamentoResponse(
                            pg.getFormaPagamento(), pg.getValor(), recebido, troco);
                })
                .toList();
        return new PedidoResponse(
                p.getId(),
                p.getDataHora(),
                p.getClienteNome(),
                p.getTelefone(),
                p.getTipo(),
                p.getStatus(),
                p.getTotal(),
                p.getDesconto() != null ? p.getDesconto() : java.math.BigDecimal.ZERO,
                p.getFormaPagamento(),
                p.getEnderecoEntrega(),
                p.isEstoqueBaixado(),
                itens,
                pagamentos);
    }

    public static PedidoItemResponse toItem(PedidoItem i) {
        return item(i);
    }

    private static PedidoItemResponse item(PedidoItem i) {
        if (i.isCombo()) {
            String nome = i.getDescricao() != null
                    ? i.getDescricao()
                    : (i.getCombo() != null ? i.getCombo().getNome() : "Combo");
            return new PedidoItemResponse(
                    null,
                    i.getCombo() != null ? i.getCombo().getId() : null,
                    nome,
                    i.getQuantidade(),
                    i.getPrecoUnitario(),
                    i.getCustoUnitario());
        }
        String nome = i.getDescricao() != null
                ? i.getDescricao()
                : (i.getProduto() != null ? i.getProduto().getNome() : "Produto");
        return new PedidoItemResponse(
                i.getProduto() != null ? i.getProduto().getId() : null,
                null,
                nome,
                i.getQuantidade(),
                i.getPrecoUnitario(),
                i.getCustoUnitario());
    }
}
