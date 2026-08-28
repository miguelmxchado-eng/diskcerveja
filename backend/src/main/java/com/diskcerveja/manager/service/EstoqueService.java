package com.diskcerveja.manager.service;

import com.diskcerveja.manager.domain.entity.ComboItem;
import com.diskcerveja.manager.domain.entity.MovimentoEstoque;
import com.diskcerveja.manager.domain.entity.Pedido;
import com.diskcerveja.manager.domain.entity.PedidoItem;
import com.diskcerveja.manager.domain.entity.Produto;
import com.diskcerveja.manager.domain.entity.Usuario;
import com.diskcerveja.manager.domain.enums.TipoMovimentoEstoque;
import com.diskcerveja.manager.repository.MovimentoEstoqueRepository;
import com.diskcerveja.manager.repository.ProdutoRepository;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EstoqueService {

    private final ProdutoRepository produtoRepository;
    private final MovimentoEstoqueRepository movimentoEstoqueRepository;

    public EstoqueService(ProdutoRepository produtoRepository, MovimentoEstoqueRepository movimentoEstoqueRepository) {
        this.produtoRepository = produtoRepository;
        this.movimentoEstoqueRepository = movimentoEstoqueRepository;
    }

    public List<MovimentoEstoque> historicoRecente() {
        return movimentoEstoqueRepository.findRecentWithProduto(PageRequest.of(0, 200));
    }

    @Transactional
    public Produto ajustar(Long produtoId, int novaQuantidade, String motivo, Usuario usuario) {
        Produto p = produtoRepository.findById(produtoId).orElseThrow(() -> new IllegalArgumentException("Produto não encontrado."));
        int diff = novaQuantidade - p.getEstoqueAtual();
        p.setEstoqueAtual(novaQuantidade);
        produtoRepository.save(p);
        MovimentoEstoque m = new MovimentoEstoque();
        m.setProduto(p);
        m.setTipo(TipoMovimentoEstoque.AJUSTE);
        m.setQuantidade(Math.abs(diff));
        m.setMotivo(motivo != null ? motivo : "Ajuste manual");
        m.setUsuario(usuario);
        movimentoEstoqueRepository.save(m);
        return p;
    }

    @Transactional
    public void registrarEntradaCompra(Long produtoId, int quantidade, String motivo, Usuario usuario) {
        if (quantidade <= 0) {
            throw new IllegalArgumentException("Quantidade inválida.");
        }
        Produto p = produtoRepository.findById(produtoId).orElseThrow(() -> new IllegalArgumentException("Produto não encontrado."));
        p.setEstoqueAtual(p.getEstoqueAtual() + quantidade);
        produtoRepository.save(p);
        MovimentoEstoque m = new MovimentoEstoque();
        m.setProduto(p);
        m.setTipo(TipoMovimentoEstoque.ENTRADA);
        m.setQuantidade(quantidade);
        m.setMotivo(motivo != null ? motivo : "Entrada");
        m.setUsuario(usuario);
        movimentoEstoqueRepository.save(m);
    }

    @Transactional
    public void baixarPorPedido(Pedido pedido, Usuario usuario) {
        pedido.getItens().forEach(item -> {
            if (item.isCombo()) {
                int mult = item.getQuantidade();
                for (ComboItem ci : item.getCombo().getItens()) {
                    int q = ci.getQuantidade() * mult;
                    baixarProduto(ci.getProduto(), q, pedido, usuario, motivoBaixaCombo(item, pedido));
                }
            } else {
                int q = quantidadeEstoque(item);
                baixarProduto(item.getProduto(), q, pedido, usuario, "Baixa pedido #" + pedido.getId());
            }
        });
    }

    private void baixarProduto(Produto p, int q, Pedido pedido, Usuario usuario, String motivo) {
        if (p.getEstoqueAtual() < q) {
            throw new IllegalStateException(
                    "Estoque insuficiente para " + p.getNome() + ". Disponível: " + p.getEstoqueAtual());
        }
        p.setEstoqueAtual(p.getEstoqueAtual() - q);
        produtoRepository.save(p);
        MovimentoEstoque m = new MovimentoEstoque();
        m.setProduto(p);
        m.setTipo(TipoMovimentoEstoque.SAIDA);
        m.setQuantidade(q);
        m.setPedido(pedido);
        m.setMotivo(motivo);
        m.setUsuario(usuario);
        movimentoEstoqueRepository.save(m);
    }

    @Transactional
    public void estornarPorPedido(Pedido pedido, Usuario usuario) {
        pedido.getItens().forEach(item -> {
            if (item.isCombo()) {
                int mult = item.getQuantidade();
                for (ComboItem ci : item.getCombo().getItens()) {
                    int q = ci.getQuantidade() * mult;
                    estornarProduto(ci.getProduto(), q, pedido, usuario, motivoEstornoCombo(item, pedido));
                }
            } else {
                int q = quantidadeEstoque(item);
                estornarProduto(item.getProduto(), q, pedido, usuario, "Estorno pedido #" + pedido.getId());
            }
        });
    }

    /**
     * Estoque é contado em unidades quando o produto tem embalagem (ex.: pack c/6).
     * Venda de pacote baixa N unidades; venda avulsa baixa 1 por item.
     */
    static int quantidadeEstoque(PedidoItem item) {
        int q = item.getQuantidade();
        if (item.isVendaUnidade()) {
            return q;
        }
        Produto p = item.getProduto();
        Integer upe = p != null ? p.getUnidadesPorEmbalagem() : null;
        if (upe != null && upe > 1) {
            return q * upe;
        }
        return q;
    }

    private void estornarProduto(Produto p, int q, Pedido pedido, Usuario usuario, String motivo) {
        p.setEstoqueAtual(p.getEstoqueAtual() + q);
        produtoRepository.save(p);
        MovimentoEstoque m = new MovimentoEstoque();
        m.setProduto(p);
        m.setTipo(TipoMovimentoEstoque.ENTRADA);
        m.setQuantidade(q);
        m.setPedido(pedido);
        m.setMotivo(motivo);
        m.setUsuario(usuario);
        movimentoEstoqueRepository.save(m);
    }

    private static String motivoBaixaCombo(PedidoItem item, Pedido pedido) {
        return "Baixa combo \"" + item.getCombo().getNome() + "\" (pedido #" + pedido.getId() + ")";
    }

    private static String motivoEstornoCombo(PedidoItem item, Pedido pedido) {
        return "Estorno combo \"" + item.getCombo().getNome() + "\" (pedido #" + pedido.getId() + ")";
    }
}
