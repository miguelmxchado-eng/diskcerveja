package com.diskcerveja.manager.service;

import com.diskcerveja.manager.domain.entity.Cliente;
import com.diskcerveja.manager.domain.entity.Combo;
import com.diskcerveja.manager.domain.entity.ComboOpcao;
import com.diskcerveja.manager.domain.entity.ComboOpcaoGrupo;
import com.diskcerveja.manager.domain.entity.Pedido;
import com.diskcerveja.manager.domain.entity.Produto;
import com.diskcerveja.manager.domain.enums.FormaPagamento;
import com.diskcerveja.manager.domain.enums.StatusPedido;
import com.diskcerveja.manager.domain.enums.TipoPedido;
import com.diskcerveja.manager.dto.ConfirmarPagamentoPublicoRequest;
import com.diskcerveja.manager.dto.InfinitePayWebhookRequest;
import com.diskcerveja.manager.dto.LojaConfigResponse;
import com.diskcerveja.manager.dto.PedidoItemRequest;
import com.diskcerveja.manager.dto.PedidoPublicoRequest;
import com.diskcerveja.manager.dto.PedidoPublicoResponse;
import com.diskcerveja.manager.dto.PedidoRequest;
import com.diskcerveja.manager.repository.ClienteRepository;
import com.diskcerveja.manager.repository.ComboRepository;
import com.diskcerveja.manager.repository.EntregaRepository;
import com.diskcerveja.manager.repository.PedidoRepository;
import com.diskcerveja.manager.repository.ProdutoRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class PedidoPublicoService {

    private static final Logger log = LoggerFactory.getLogger(PedidoPublicoService.class);

    private final ConfigSistemaService configSistemaService;
    private final PedidoService pedidoService;
    private final ProdutoRepository produtoRepository;
    private final ComboRepository comboRepository;
    private final ClienteRepository clienteRepository;
    private final PedidoRepository pedidoRepository;
    private final InfinitePayService infinitePayService;
    private final ZonaEntregaService zonaEntregaService;
    private final EntregaRepository entregaRepository;
    private final ContaClientePublicoService contaClientePublicoService;
    private final TransactionTemplate transactionTemplate;

    public PedidoPublicoService(
            ConfigSistemaService configSistemaService,
            PedidoService pedidoService,
            ProdutoRepository produtoRepository,
            ComboRepository comboRepository,
            ClienteRepository clienteRepository,
            PedidoRepository pedidoRepository,
            InfinitePayService infinitePayService,
            ZonaEntregaService zonaEntregaService,
            EntregaRepository entregaRepository,
            ContaClientePublicoService contaClientePublicoService,
            PlatformTransactionManager transactionManager) {
        this.configSistemaService = configSistemaService;
        this.pedidoService = pedidoService;
        this.produtoRepository = produtoRepository;
        this.comboRepository = comboRepository;
        this.clienteRepository = clienteRepository;
        this.pedidoRepository = pedidoRepository;
        this.infinitePayService = infinitePayService;
        this.zonaEntregaService = zonaEntregaService;
        this.entregaRepository = entregaRepository;
        this.contaClientePublicoService = contaClientePublicoService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * Cria o pedido (commit) e só então chama a InfinitePay — evita segurar a
     * transação do banco durante a HTTP externa.
     */
    public PedidoPublicoResponse criar(PedidoPublicoRequest req) {
        return criar(req, null);
    }

    public PedidoPublicoResponse criar(PedidoPublicoRequest req, String authorizationHeader) {
        Pedido salvo = transactionTemplate.execute(status -> persistirPedido(req, authorizationHeader));
        if (salvo == null) {
            throw new IllegalStateException("Não foi possível criar o pedido.");
        }
        // Usa a taxa já gravada na entrega — não recalcula (evita divergir do total pago).
        BigDecimal taxa = taxaEntregaDoPedido(salvo.getId());

        // Reabre leitura com itens inicializados (evita LazyInitialization fora da TX).
        Pedido paraCheckout = transactionTemplate.execute(status -> pedidoRepository
                .findByIdWithItens(salvo.getId())
                .orElseThrow(() -> new IllegalStateException("Pedido não encontrado após criar.")));
        if (paraCheckout == null) {
            throw new IllegalStateException("Pedido não encontrado após criar.");
        }

        String base = resolverBaseUrl();
        boolean retirada = paraCheckout.getTipo() == TipoPedido.RETIRADA;
        String checkoutUrl;
        try {
            checkoutUrl = infinitePayService.criarLinkCheckout(
                    paraCheckout,
                    paraCheckout.getClienteNome(),
                    paraCheckout.getTelefone(),
                    base,
                    retirada ? null : req.cep(),
                    retirada ? null : req.logradouro(),
                    retirada ? null : req.bairro(),
                    retirada ? null : req.numero(),
                    retirada ? null : req.complemento());
        } catch (RuntimeException ex) {
            try {
                pedidoService.mudarStatus(salvo.getId(), StatusPedido.CANCELADO, null);
            } catch (Exception cancelEx) {
                log.warn("Falha ao cancelar pedido {} após erro InfinitePay", salvo.getId(), cancelEx);
            }
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

    private Pedido persistirPedido(PedidoPublicoRequest req, String authorizationHeader) {
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
        var autenticadoOpt = contaClientePublicoService.clienteAutenticado(authorizationHeader);
        if (autenticadoOpt.isPresent()) {
            // Pedido logado usa o WhatsApp da conta — evita divergir do cadastro.
            telefone = autenticadoOpt.get().getTelefone() != null
                    ? autenticadoOpt.get().getTelefone().trim()
                    : telefone;
        }
        String digits = soDigitos(telefone);
        if (digits.length() < 10) {
            throw new IllegalArgumentException("Informe um telefone válido com DDD.");
        }

        TipoPedido tipoPedido = req.tipo() == TipoPedido.RETIRADA ? TipoPedido.RETIRADA : TipoPedido.ENTREGA;
        if (req.tipo() != null && req.tipo() != TipoPedido.ENTREGA && req.tipo() != TipoPedido.RETIRADA) {
            throw new IllegalArgumentException("Tipo de pedido inválido no cardápio.");
        }

        String endereco;
        BigDecimal taxaEntrega;
        if (tipoPedido == TipoPedido.RETIRADA) {
            endereco = "Retirada na loja";
            taxaEntrega = BigDecimal.ZERO;
        } else {
            endereco = req.enderecoEntrega() != null ? req.enderecoEntrega().trim() : "";
            if (endereco.length() < 8) {
                throw new IllegalArgumentException("Informe o endereço completo para entrega.");
            }
            taxaEntrega = zonaEntregaService.taxaObrigatoria(req.cep(), req.bairro());
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
                String observacaoItem = resolverObservacaoCombo(c, item);
                subtotal = subtotal.add(c.getPrecoVenda().multiply(BigDecimal.valueOf(item.quantidade())));
                itens.add(new PedidoItemRequest(null, c.getId(), item.quantidade(), null, observacaoItem));
            } else {
                throw new IllegalArgumentException("Tipo de item inválido: " + item.tipo());
            }
        }

        if (loja.pedidoMinimo() != null && subtotal.compareTo(loja.pedidoMinimo()) < 0) {
            throw new IllegalArgumentException(
                    "Pedido mínimo é R$ " + loja.pedidoMinimo().toPlainString() + ".");
        }

        String enderecoCliente =
                tipoPedido == TipoPedido.RETIRADA
                        ? null
                        : (req.enderecoEntrega() != null ? req.enderecoEntrega().trim() : null);
        Long clienteId = resolverClienteId(
                req, autenticadoOpt.orElse(null), nome, telefone, enderecoCliente);

        PedidoRequest pedidoReq = new PedidoRequest(
                clienteId,
                nome,
                telefone,
                tipoPedido,
                req.formaPagamento() != null ? req.formaPagamento() : FormaPagamento.PIX,
                endereco,
                taxaEntrega,
                BigDecimal.ZERO,
                null,
                itens,
                null);

        return pedidoService.criar(pedidoReq, null, false);
    }

    public void confirmarPagamentoWebhook(InfinitePayWebhookRequest body) {
        if (body == null || body.order_nsu() == null || body.order_nsu().isBlank()) {
            throw new IllegalArgumentException("Webhook sem order_nsu.");
        }
        if (body.transaction_nsu() == null
                || body.transaction_nsu().isBlank()
                || body.invoice_slug() == null
                || body.invoice_slug().isBlank()) {
            throw new IllegalArgumentException("Webhook incompleto (transaction_nsu/invoice_slug).");
        }

        // Confirma na InfinitePay antes de marcar pago (webhook não tem assinatura).
        var check = infinitePayService.verificarPagamento(
                body.order_nsu().trim(), body.transaction_nsu().trim(), body.invoice_slug().trim());
        if (!check.paid()) {
            throw new IllegalArgumentException("Pagamento não confirmado na InfinitePay.");
        }

        transactionTemplate.executeWithoutResult(status -> confirmarPagamentoWebhookTx(body, check.amountCents()));
    }

    private void confirmarPagamentoWebhookTx(InfinitePayWebhookRequest body, Integer amountFromCheck) {
        Long id;
        try {
            id = Long.valueOf(body.order_nsu().trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("order_nsu inválido.");
        }
        Pedido p = pedidoRepository.findById(id).orElse(null);
        if (p == null) {
            log.warn("Webhook InfinitePay para pedido inexistente: {}", body.order_nsu());
            return;
        }
        if (p.getUsuario() != null) {
            log.warn("Webhook InfinitePay ignorado para pedido interno #{}", id);
            return;
        }
        if (p.getStatus() == StatusPedido.CANCELADO) {
            log.warn("Webhook InfinitePay para pedido cancelado #{}", id);
            return;
        }
        if (p.isPagamentoConfirmado()) {
            return;
        }
        // Só confia no valor do payment_check — o body do webhook não é autenticado.
        garantirValorPago(p, amountFromCheck);
        marcarPago(p, body.transaction_nsu(), body.capture_method());
    }

    public Map<String, Object> statusPagamentoPublico(Long pedidoId) {
        if (pedidoId == null) {
            throw new IllegalArgumentException("Pedido inválido.");
        }
        Pedido p = pedidoRepository
                .findById(pedidoId)
                .orElseThrow(() -> new IllegalArgumentException("Pedido não encontrado."));
        // Só pedidos do cardápio (sem usuário de balcão).
        if (p.getUsuario() != null) {
            throw new IllegalArgumentException("Pedido não encontrado.");
        }
        return Map.of(
                "id",
                p.getId(),
                "pagamentoConfirmado",
                p.isPagamentoConfirmado(),
                "cancelado",
                p.getStatus() == StatusPedido.CANCELADO,
                "total",
                p.getTotal(),
                "taxaEntrega",
                taxaEntregaDoPedido(p.getId()),
                "formaPagamento",
                p.getFormaPagamento() != null ? p.getFormaPagamento().name() : "PIX",
                "mensagem",
                p.isPagamentoConfirmado()
                        ? "Pagamento confirmado! Em breve saímos para entrega."
                        : "Aguardando confirmação do pagamento.");
    }

    public PedidoPublicoResponse confirmarPagamentoRetorno(Long pedidoId, ConfirmarPagamentoPublicoRequest req) {
        if (pedidoId == null) {
            throw new IllegalArgumentException("Pedido inválido.");
        }
        String tx = req != null ? req.transactionNsu() : null;
        String slug = req != null ? req.slug() : null;
        String capture = req != null ? req.captureMethod() : null;
        if (tx == null || tx.isBlank() || slug == null || slug.isBlank()) {
            throw new IllegalArgumentException("Dados de pagamento incompletos. Aguarde a confirmação.");
        }

        PedidoPublicoResponse jaPago = transactionTemplate.execute(status -> {
            Pedido p = exigirPedidoPublico(pedidoId);
            if (p.getStatus() == StatusPedido.CANCELADO) {
                throw new IllegalStateException("Este pedido foi cancelado.");
            }
            if (p.isPagamentoConfirmado()) {
                return toPublicoOk(p, "Pagamento já confirmado. Em breve saímos para entrega.");
            }
            return null;
        });
        if (jaPago != null) {
            return jaPago;
        }

        var check = infinitePayService.verificarPagamento(String.valueOf(pedidoId), tx, slug);
        if (!check.paid()) {
            throw new IllegalStateException(
                    "Pagamento ainda não confirmado. Se já pagou, aguarde alguns segundos.");
        }

        return transactionTemplate.execute(status -> {
            Pedido p = exigirPedidoPublico(pedidoId);
            if (!p.isPagamentoConfirmado()) {
                garantirValorPago(p, check.amountCents());
                marcarPago(p, tx, capture);
            }
            return toPublicoOk(p, "Pagamento do pedido #" + p.getId() + " confirmado! Em breve saímos para entrega.");
        });
    }

    private Pedido exigirPedidoPublico(Long pedidoId) {
        Pedido p = pedidoRepository
                .findById(pedidoId)
                .orElseThrow(() -> new IllegalArgumentException("Pedido não encontrado."));
        if (p.getUsuario() != null) {
            throw new IllegalArgumentException("Pedido não encontrado.");
        }
        return p;
    }

    /**
     * Exige valor pago (centavos) vindo do payment_check da InfinitePay ≥ total do pedido.
     * Sem valor autenticado → rejeita (evita subpagamento e body de webhook forjado).
     */
    private void garantirValorPago(Pedido p, Integer amountFromCheck) {
        int esperado = toCents(p.getTotal());
        if (amountFromCheck == null) {
            log.error("Pagamento sem valor no payment_check para pedido #{} (esperado={} centavos)", p.getId(), esperado);
            throw new IllegalArgumentException("Pagamento sem valor confirmado. Aguarde e tente de novo.");
        }
        if (amountFromCheck < esperado) {
            log.error(
                    "Pagamento com valor menor que o pedido #{}: informado={} esperado={}",
                    p.getId(),
                    amountFromCheck,
                    esperado);
            throw new IllegalArgumentException("Valor pago inferior ao pedido.");
        }
    }

    private void marcarPago(Pedido p, String transactionNsu, String captureMethod) {
        p.setPagamentoConfirmado(true);
        if (transactionNsu != null && !transactionNsu.isBlank()) {
            p.setPagamentoRef(transactionNsu);
        }
        FormaPagamento confirmada = null;
        if (captureMethod != null) {
            if ("pix".equalsIgnoreCase(captureMethod)) {
                confirmada = FormaPagamento.PIX;
            } else if ("credit_card".equalsIgnoreCase(captureMethod)) {
                confirmada = FormaPagamento.CARTAO;
            }
        }
        if (confirmada != null) {
            p.setFormaPagamento(confirmada);
            if (p.getPagamentos().size() == 1) {
                p.getPagamentos().get(0).setFormaPagamento(confirmada);
            }
        }
        if (p.getStatus() == StatusPedido.ABERTO) {
            p.setStatus(StatusPedido.EM_PREPARO);
        }
        pedidoRepository.save(p);
    }

    private PedidoPublicoResponse toPublicoOk(Pedido p, String mensagem) {
        return new PedidoPublicoResponse(
                p.getId(),
                p.getDataHora(),
                p.getStatus(),
                p.getTotal(),
                taxaEntregaDoPedido(p.getId()),
                p.getFormaPagamento(),
                mensagem,
                null,
                true);
    }

    private BigDecimal taxaEntregaDoPedido(Long pedidoId) {
        return entregaRepository
                .findByPedido_Id(pedidoId)
                .map(e -> e.getTaxaEntrega() != null ? e.getTaxaEntrega() : BigDecimal.ZERO)
                .orElse(BigDecimal.ZERO);
    }

    private String resolverBaseUrl() {
        String configured = configSistemaService.getPublicBaseUrl();
        if (configured != null && !configured.isBlank()) {
            return configured.replaceAll("/$", "");
        }
        // Não confiar em X-Forwarded-* / Host do cliente: risco de sequestro de redirect/webhook.
        throw new IllegalStateException(
                "URL pública da loja não configurada. Defina em Configurações (loja.public_base_url).");
    }

    /**
     * Vincula pedido a cliente autenticado (atualiza endereço) ou faz upsert por telefone.
     */
    private Long resolverClienteId(
            PedidoPublicoRequest req,
            Cliente autenticado,
            String nome,
            String telefone,
            String enderecoCliente) {
        if (autenticado != null) {
            Cliente c = autenticado;
            if (nome != null && !nome.isBlank()) {
                c.setNome(nome.trim());
            }
            if (tipoEntregaComEndereco(req) && enderecoCliente != null && !enderecoCliente.isBlank()) {
                c.setEndereco(enderecoCliente);
                if (req.cep() != null) {
                    String cepDigits = soDigitos(req.cep());
                    c.setCep(
                            cepDigits.length() == 8
                                    ? cepDigits.substring(0, 5) + "-" + cepDigits.substring(5)
                                    : req.cep());
                }
                if (req.logradouro() != null && !req.logradouro().isBlank()) {
                    c.setLogradouro(req.logradouro().trim());
                }
                if (req.numero() != null && !req.numero().isBlank()) {
                    c.setNumero(req.numero().trim());
                }
                if (req.complemento() != null) {
                    c.setComplemento(req.complemento().isBlank() ? null : req.complemento().trim());
                }
                if (req.bairro() != null && !req.bairro().isBlank()) {
                    c.setBairro(req.bairro().trim());
                }
                if (req.cidade() != null && !req.cidade().isBlank()) {
                    c.setCidade(req.cidade().trim());
                }
                if (req.uf() != null && !req.uf().isBlank()) {
                    c.setUf(req.uf().trim().toUpperCase(Locale.ROOT));
                }
            }
            if (req.observacao() != null
                    && !req.observacao().isBlank()
                    && (c.getObservacao() == null || c.getObservacao().isBlank())) {
                c.setObservacao(req.observacao().trim());
            }
            return clienteRepository.save(c).getId();
        }
        return upsertCliente(nome, telefone, enderecoCliente, req.observacao());
    }

    private static boolean tipoEntregaComEndereco(PedidoPublicoRequest req) {
        return req.tipo() != TipoPedido.RETIRADA;
    }

    private Long upsertCliente(String nome, String telefone, String endereco, String observacao) {
        String digits = soDigitos(telefone);
        return clienteRepository
                .findAtivoByTelefoneDigits(digits)
                .map(c -> {
                    // Só preenche endereço/obs se o cadastro estiver vazio — não sobrescreve.
                    if ((c.getEndereco() == null || c.getEndereco().isBlank())
                            && endereco != null
                            && !endereco.isBlank()) {
                        c.setEndereco(endereco);
                    }
                    if ((c.getObservacao() == null || c.getObservacao().isBlank())
                            && observacao != null
                            && !observacao.isBlank()) {
                        c.setObservacao(observacao.trim());
                    }
                    return clienteRepository.save(c).getId();
                })
                .orElseGet(() -> {
                    Cliente c = new Cliente();
                    c.setNome(nome);
                    c.setTelefone(telefone);
                    c.setEndereco(endereco);
                    if (observacao != null && !observacao.isBlank()) {
                        c.setObservacao(observacao.trim());
                    }
                    c.setAtivo(true);
                    return clienteRepository.save(c).getId();
                });
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

    /**
     * Valida escolhas de combo configurável e monta o texto gravado no item
     * (ex.: "Red Bull: Melancia; Gelo: Comum").
     */
    private static String resolverObservacaoCombo(Combo c, PedidoPublicoRequest.Item item) {
        if (!c.isConfiguravel() || c.getGruposOpcao() == null || c.getGruposOpcao().isEmpty()) {
            if (item.observacao() != null && !item.observacao().isBlank()) {
                return truncarObs(item.observacao().trim());
            }
            return null;
        }

        List<Long> opcaoIds = item.opcaoIds() != null ? item.opcaoIds() : List.of();
        if (opcaoIds.isEmpty()) {
            throw new IllegalArgumentException(
                    "Monte as opções do combo \"" + c.getNome() + "\" antes de pedir.");
        }

        Map<Long, ComboOpcao> porId = new java.util.HashMap<>();
        for (ComboOpcaoGrupo g : c.getGruposOpcao()) {
            for (ComboOpcao o : g.getOpcoes()) {
                if (o.isAtivo()) {
                    porId.put(o.getId(), o);
                }
            }
        }

        Map<Long, List<ComboOpcao>> escolhidasPorGrupo = new java.util.LinkedHashMap<>();
        for (ComboOpcaoGrupo g : c.getGruposOpcao()) {
            escolhidasPorGrupo.put(g.getId(), new ArrayList<>());
        }

        for (Long oid : opcaoIds) {
            ComboOpcao op = porId.get(oid);
            if (op == null) {
                throw new IllegalArgumentException(
                        "Opção inválida no combo \"" + c.getNome() + "\".");
            }
            Long gid = op.getGrupo().getId();
            List<ComboOpcao> lista = escolhidasPorGrupo.get(gid);
            if (lista == null) {
                throw new IllegalArgumentException(
                        "Opção inválida no combo \"" + c.getNome() + "\".");
            }
            lista.add(op);
        }

        List<String> partes = new ArrayList<>();
        for (ComboOpcaoGrupo g : c.getGruposOpcao()) {
            List<ComboOpcao> escolhidas = escolhidasPorGrupo.getOrDefault(g.getId(), List.of());
            int n = escolhidas.size();
            int min = g.isObrigatorio() ? Math.max(1, g.getMinimo()) : g.getMinimo();
            int max = Math.max(1, g.getMaximo());
            if (n < min) {
                throw new IllegalArgumentException(
                        "Escolha " + (min == 1 ? "uma opção" : min + " opções")
                                + " em \"" + g.getNome() + "\" do combo " + c.getNome() + ".");
            }
            if (n > max) {
                throw new IllegalArgumentException(
                        "Escolha no máximo " + max + " em \"" + g.getNome() + "\".");
            }
            if (n > 0) {
                String rotulos = escolhidas.stream()
                        .map(ComboOpcao::getRotulo)
                        .reduce((a, b) -> a + ", " + b)
                        .orElse("");
                partes.add(g.getNome() + ": " + rotulos);
            }
        }

        String texto = String.join("; ", partes);
        if (texto.isBlank()) {
            throw new IllegalArgumentException(
                    "Monte as opções do combo \"" + c.getNome() + "\" antes de pedir.");
        }
        return truncarObs(texto);
    }

    private static String truncarObs(String obs) {
        return obs.length() > 500 ? obs.substring(0, 500) : obs;
    }

    private static String soDigitos(String raw) {
        if (raw == null) {
            return "";
        }
        String d = raw.replaceAll("\\D", "");
        if (d.startsWith("55") && d.length() >= 12) {
            d = d.substring(2);
        }
        return d;
    }

    private static int toCents(BigDecimal value) {
        return value.movePointRight(2).setScale(0, RoundingMode.HALF_UP).intValueExact();
    }
}
