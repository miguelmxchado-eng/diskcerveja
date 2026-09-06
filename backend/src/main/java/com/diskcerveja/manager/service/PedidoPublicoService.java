package com.diskcerveja.manager.service;

import com.diskcerveja.manager.domain.entity.Cliente;
import com.diskcerveja.manager.domain.entity.Combo;
import com.diskcerveja.manager.domain.entity.Pedido;
import com.diskcerveja.manager.domain.entity.Produto;
import com.diskcerveja.manager.domain.enums.FormaPagamento;
import com.diskcerveja.manager.domain.enums.StatusPedido;
import com.diskcerveja.manager.domain.enums.TipoPedido;
import com.diskcerveja.manager.dto.InfinitePayWebhookRequest;
import com.diskcerveja.manager.dto.LojaConfigResponse;
import com.diskcerveja.manager.dto.PedidoItemRequest;
import com.diskcerveja.manager.dto.PedidoPublicoRequest;
import com.diskcerveja.manager.dto.PedidoPublicoResponse;
import com.diskcerveja.manager.dto.PedidoRequest;
import com.diskcerveja.manager.repository.ClienteRepository;
import com.diskcerveja.manager.repository.ComboRepository;
import com.diskcerveja.manager.repository.PedidoRepository;
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
    private final PedidoRepository pedidoRepository;
    private final InfinitePayService infinitePayService;

    public PedidoPublicoService(
            ConfigSistemaService configSistemaService,
            PedidoService pedidoService,
            ProdutoRepository produtoRepository,
            ComboRepository comboRepository,
            ClienteRepository clienteRepository,
            PedidoRepository pedidoRepository,
            InfinitePayService infinitePayService) {
        this.configSistemaService = configSistemaService;
        this.pedidoService = pedidoService;
        this.produtoRepository = produtoRepository;
        this.comboRepository = comboRepository;
        this.clienteRepository = clienteRepository;
        this.pedidoRepository = pedidoRepository;
        this.infinitePayService = infinitePayService;
    }

    @Transactional
    public PedidoPublicoResponse criar(PedidoPublicoRequest req, String publicBaseUrl) {
        LojaConfigResponse loja = configSistemaService.getLoja();
        if (!loja.aberta()) {
            throw new IllegalStateException("A loja está fechada no momento. Tente mais tarde.");
        }

        if (req.formaPagamento() == FormaPagamento.DINHEIRO) {
            throw new IllegalArgumentException("Pagamento em dinheiro não está disponível no cardápio.");
        }
        if (!configSistemaService.isPagamentoOnlineAtivo()) {
            throw new IllegalStateException(
                    "Pagamento online não está configurado. Configure a InfiniteTag em Configurações.");
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

        String base = resolverBaseUrl(publicBaseUrl);
        String checkoutUrl;
        try {
            checkoutUrl = infinitePayService.criarLinkCheckout(salvo, nome, telefone, base);
        } catch (RuntimeException ex) {
            pedidoService.mudarStatus(salvo.getId(), StatusPedido.CANCELADO, null);
            throw ex;
        }
        if (checkoutUrl == null || checkoutUrl.isBlank()) {
            pedidoService.mudarStatus(salvo.getId(), StatusPedido.CANCELADO, null);
            throw new IllegalStateException("Não foi possível gerar o link de pagamento.");
        }

        return new PedidoPublicoResponse(
                salvo.getId(),
                salvo.getDataHora(),
                salvo.getStatus(),
                salvo.getTotal(),
                taxa,
                salvo.getFormaPagamento(),
                "Pedido #" + salvo.getId() + " criado. Finalize o pagamento no InfinitePay.",
                checkoutUrl,
                true);
    }

    @Transactional
    public void confirmarPagamentoWebhook(InfinitePayWebhookRequest body) {
        if (body == null || body.order_nsu() == null || body.order_nsu().isBlank()) {
            throw new IllegalArgumentException("Webhook sem order_nsu.");
        }
        Long id;
        try {
            id = Long.valueOf(body.order_nsu().trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("order_nsu inválido.");
        }
        Pedido p = pedidoRepository
                .findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Pedido não encontrado."));
        if (p.getStatus() == StatusPedido.CANCELADO) {
            throw new IllegalStateException("Pedido cancelado.");
        }
        if (p.isPagamentoConfirmado()) {
            return;
        }
        p.setPagamentoConfirmado(true);
        if (body.transaction_nsu() != null) {
            p.setPagamentoRef(body.transaction_nsu());
        }
        if (body.capture_method() != null) {
            if ("pix".equalsIgnoreCase(body.capture_method())) {
                p.setFormaPagamento(FormaPagamento.PIX);
            } else if ("credit_card".equalsIgnoreCase(body.capture_method())) {
                p.setFormaPagamento(FormaPagamento.CARTAO);
            }
        }
        if (p.getStatus() == StatusPedido.ABERTO) {
            p.setStatus(StatusPedido.EM_PREPARO);
        }
        pedidoRepository.save(p);
    }

    private String resolverBaseUrl(String fromRequest) {
        String configured = configSistemaService.getPublicBaseUrl();
        if (!configured.isBlank()) {
            return configured;
        }
        if (fromRequest != null && !fromRequest.isBlank()) {
            return fromRequest.replaceAll("/$", "");
        }
        return "https://15.204.123.70";
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
