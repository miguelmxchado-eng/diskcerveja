package com.diskcerveja.manager.service;

import com.diskcerveja.manager.domain.entity.Cliente;
import com.diskcerveja.manager.domain.entity.Combo;
import com.diskcerveja.manager.domain.entity.Pedido;
import com.diskcerveja.manager.domain.entity.Produto;
import com.diskcerveja.manager.domain.enums.TipoPedido;
import com.diskcerveja.manager.dto.LojaConfigResponse;
import com.diskcerveja.manager.dto.PedidoItemRequest;
import com.diskcerveja.manager.dto.PedidoPublicoRequest;
import com.diskcerveja.manager.dto.PedidoPublicoResponse;
import com.diskcerveja.manager.dto.PedidoRequest;
import com.diskcerveja.manager.repository.ClienteRepository;
import com.diskcerveja.manager.repository.ComboRepository;
import com.diskcerveja.manager.repository.ProdutoRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PedidoPublicoService {

    private final ConfigSistemaService configSistemaService;
    private final PedidoService pedidoService;
    private final ProdutoRepository produtoRepository;
    private final ComboRepository comboRepository;
    private final ClienteRepository clienteRepository;

    public PedidoPublicoService(
            ConfigSistemaService configSistemaService,
            PedidoService pedidoService,
            ProdutoRepository produtoRepository,
            ComboRepository comboRepository,
            ClienteRepository clienteRepository) {
        this.configSistemaService = configSistemaService;
        this.pedidoService = pedidoService;
        this.produtoRepository = produtoRepository;
        this.comboRepository = comboRepository;
        this.clienteRepository = clienteRepository;
    }

    @Transactional
    public PedidoPublicoResponse criar(PedidoPublicoRequest req) {
        LojaConfigResponse loja = configSistemaService.getLoja();
        if (!loja.aberta()) {
            throw new IllegalStateException("A loja está fechada no momento. Tente mais tarde.");
        }

        String nome = req.clienteNome().trim();
        String telefone = req.telefone().trim();
        String endereco = req.enderecoEntrega().trim();
        String digits = soDigitos(telefone);
        if (digits.length() < 10) {
            throw new IllegalArgumentException("Informe um telefone válido com DDD.");
        }
        if (endereco.length() < 8) {
            throw new IllegalArgumentException("Informe o endereço completo para entrega.");
        }

        if (req.observacao() != null && !req.observacao().isBlank()) {
            endereco = endereco + " — Obs: " + req.observacao().trim();
        }

        List<PedidoItemRequest> itens = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        for (PedidoPublicoRequest.Item item : req.itens()) {
            String tipo = item.tipo().trim().toUpperCase(Locale.ROOT);
            if ("PRODUTO".equals(tipo)) {
                Produto p = produtoRepository
                        .findById(item.id())
                        .orElseThrow(() -> new IllegalArgumentException("Produto inválido."));
                if (!p.isAtivo() || !p.isVisivelCardapio()) {
                    throw new IllegalArgumentException("Produto indisponível no cardápio: " + p.getNome());
                }
                boolean unidade = Boolean.TRUE.equals(item.vendaUnidade());
                if (unidade && !p.permiteVendaUnidade()) {
                    throw new IllegalArgumentException("Produto sem venda por unidade: " + p.getNome());
                }
                garantirEstoqueProduto(p, item.quantidade(), unidade);
                BigDecimal preco = unidade ? p.getPrecoUnidade() : p.getPreco();
                subtotal = subtotal.add(preco.multiply(BigDecimal.valueOf(item.quantidade())));
                itens.add(new PedidoItemRequest(p.getId(), null, item.quantidade(), unidade));
            } else if ("COMBO".equals(tipo)) {
                Combo c = comboRepository
                        .findByIdWithItens(item.id())
                        .orElseThrow(() -> new IllegalArgumentException("Combo inválido."));
                if (!c.isAtivo() || !c.isVisivelCardapio()) {
                    throw new IllegalArgumentException("Combo indisponível no cardápio: " + c.getNome());
                }
                garantirEstoqueCombo(c, item.quantidade());
                subtotal = subtotal.add(c.getPrecoVenda().multiply(BigDecimal.valueOf(item.quantidade())));
                itens.add(new PedidoItemRequest(null, c.getId(), item.quantidade(), null));
            } else {
                throw new IllegalArgumentException("Tipo de item inválido: " + item.tipo());
            }
        }

        if (loja.pedidoMinimo() != null && subtotal.compareTo(loja.pedidoMinimo()) < 0) {
            throw new IllegalArgumentException(
                    "Pedido mínimo é R$ " + loja.pedidoMinimo().toPlainString() + ".");
        }

        Long clienteId = upsertCliente(nome, telefone, req.enderecoEntrega().trim(), req.observacao());

        PedidoRequest pedidoReq = new PedidoRequest(
                clienteId,
                nome,
                telefone,
                TipoPedido.ENTREGA,
                req.formaPagamento(),
                endereco,
                loja.taxaEntrega() != null ? loja.taxaEntrega() : BigDecimal.ZERO,
                BigDecimal.ZERO,
                null,
                itens);

        Pedido salvo = pedidoService.criar(pedidoReq, null, false);
        BigDecimal taxa = loja.taxaEntrega() != null ? loja.taxaEntrega() : BigDecimal.ZERO;
        return new PedidoPublicoResponse(
                salvo.getId(),
                salvo.getDataHora(),
                salvo.getStatus(),
                salvo.getTotal(),
                taxa,
                salvo.getFormaPagamento(),
                "Pedido #" + salvo.getId() + " recebido! Em breve entraremos em contato.");
    }

    private Long upsertCliente(String nome, String telefone, String endereco, String observacao) {
        String digits = soDigitos(telefone);
        Cliente c = clienteRepository.findAtivoByTelefoneDigits(digits).orElseGet(Cliente::new);
        c.setNome(nome);
        c.setTelefone(telefone);
        c.setEndereco(endereco);
        if (observacao != null && !observacao.isBlank()) {
            c.setObservacao(observacao.trim());
        }
        c.setAtivo(true);
        return clienteRepository.save(c).getId();
    }

    private static void garantirEstoqueProduto(Produto p, int quantidade, boolean unidade) {
        int estoque = p.getEstoqueAtual();
        if (unidade) {
            if (estoque < quantidade) {
                throw new IllegalArgumentException("Estoque insuficiente: " + p.getNome());
            }
            return;
        }
        int upe = p.getUnidadesPorEmbalagem() != null && p.getUnidadesPorEmbalagem() > 1
                ? p.getUnidadesPorEmbalagem()
                : 1;
        if (estoque < quantidade * upe) {
            throw new IllegalArgumentException("Estoque insuficiente: " + p.getNome());
        }
    }

    private static void garantirEstoqueCombo(Combo c, int quantidade) {
        if (c.getItens() == null || c.getItens().isEmpty()) {
            throw new IllegalArgumentException("Combo sem produtos: " + c.getNome());
        }
        for (var ci : c.getItens()) {
            Produto p = ci.getProduto();
            if (p == null || !p.isAtivo()) {
                throw new IllegalArgumentException("Combo indisponível: " + c.getNome());
            }
            int preciso = ci.getQuantidade() * quantidade;
            if (p.getEstoqueAtual() < preciso) {
                throw new IllegalArgumentException("Estoque insuficiente no combo: " + c.getNome());
            }
        }
    }

    private static String soDigitos(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replaceAll("\\D", "");
    }
}
