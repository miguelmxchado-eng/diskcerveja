package com.diskcerveja.manager.service;

import com.diskcerveja.manager.domain.entity.Cliente;
import com.diskcerveja.manager.domain.entity.Combo;
import com.diskcerveja.manager.domain.entity.Entrega;
import com.diskcerveja.manager.domain.entity.Pedido;
import com.diskcerveja.manager.domain.entity.PedidoItem;
import com.diskcerveja.manager.domain.entity.Produto;
import com.diskcerveja.manager.domain.entity.Usuario;
import com.diskcerveja.manager.domain.enums.StatusEntrega;
import com.diskcerveja.manager.domain.enums.StatusPedido;
import com.diskcerveja.manager.domain.enums.TipoPedido;
import com.diskcerveja.manager.dto.PedidoItemRequest;
import com.diskcerveja.manager.dto.PedidoRequest;
import com.diskcerveja.manager.dto.PedidoUpdateRequest;
import com.diskcerveja.manager.repository.ClienteRepository;
import com.diskcerveja.manager.repository.ComboRepository;
import com.diskcerveja.manager.repository.EntregaRepository;
import com.diskcerveja.manager.repository.PedidoRepository;
import com.diskcerveja.manager.repository.ProdutoRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PedidoService {

    private final PedidoRepository pedidoRepository;
    private final ProdutoRepository produtoRepository;
    private final ComboRepository comboRepository;
    private final EntregaRepository entregaRepository;
    private final ClienteRepository clienteRepository;
    private final ConfigSistemaService configSistemaService;
    private final CaixaSessaoService caixaSessaoService;
    private final EstoqueService estoqueService;

    public PedidoService(
            PedidoRepository pedidoRepository,
            ProdutoRepository produtoRepository,
            ComboRepository comboRepository,
            EntregaRepository entregaRepository,
            ClienteRepository clienteRepository,
            ConfigSistemaService configSistemaService,
            CaixaSessaoService caixaSessaoService,
            EstoqueService estoqueService) {
        this.pedidoRepository = pedidoRepository;
        this.produtoRepository = produtoRepository;
        this.comboRepository = comboRepository;
        this.entregaRepository = entregaRepository;
        this.clienteRepository = clienteRepository;
        this.configSistemaService = configSistemaService;
        this.caixaSessaoService = caixaSessaoService;
        this.estoqueService = estoqueService;
    }

    @Transactional(readOnly = true)
    public List<Pedido> listarRecentes() {
        return pedidoRepository.findTop200ByOrderByDataHoraDesc();
    }

    @Transactional(readOnly = true)
    public Pedido buscar(Long id) {
        return pedidoRepository
                .findByIdWithItens(id)
                .orElseThrow(() -> new IllegalArgumentException("Pedido não encontrado."));
    }

    @Transactional
    public Pedido criar(PedidoRequest dto, Usuario usuario) {
        if (dto.tipo() == null || dto.formaPagamento() == null) {
            throw new IllegalArgumentException("Tipo e forma de pagamento são obrigatórios.");
        }
        if (configSistemaService.isCaixaObrigatorio() && caixaSessaoService.sessaoAbertaHoje() == null) {
            throw new IllegalStateException("Abra o caixa antes de registrar pedidos.");
        }
        validarItens(dto.itens());
        Pedido p = new Pedido();
        aplicarClienteNoPedido(p, dto);
        p.setTipo(dto.tipo());
        p.setFormaPagamento(dto.formaPagamento());
        if (dto.enderecoEntrega() != null && !dto.enderecoEntrega().isBlank()) {
            p.setEnderecoEntrega(dto.enderecoEntrega().trim());
        } else if (p.getEnderecoEntrega() == null && p.getCliente() != null) {
            p.setEnderecoEntrega(p.getCliente().getEndereco());
        }
        p.setStatus(StatusPedido.ABERTO);
        p.setUsuario(usuario);
        aplicarItens(p, dto.itens());
        BigDecimal taxa = BigDecimal.ZERO;
        if (dto.tipo() == TipoPedido.ENTREGA) {
            taxa = dto.taxaEntrega() != null ? dto.taxaEntrega() : BigDecimal.ZERO;
            if (dto.enderecoEntrega() == null || dto.enderecoEntrega().isBlank()) {
                throw new IllegalArgumentException("Endereço de entrega é obrigatório para pedidos de entrega.");
            }
        }
        p.setTotal(calcularTotalItens(p).add(taxa));
        Pedido salvo = pedidoRepository.save(p);
        if (dto.tipo() == TipoPedido.ENTREGA) {
            Entrega e = new Entrega();
            e.setPedido(salvo);
            e.setTaxaEntrega(taxa);
            e.setStatus(StatusEntrega.PENDENTE);
            if (dto.entregadorNome() != null) {
                e.setEntregadorNome(dto.entregadorNome());
            }
            entregaRepository.save(e);
        }
        return pedidoRepository.findByIdWithItens(salvo.getId()).orElse(salvo);
    }

    @Transactional
    public Pedido atualizar(Long id, PedidoUpdateRequest dto) {
        Pedido p = buscar(id);
        garantirEditavel(p);
        if (dto.clienteNome() != null) {
            p.setClienteNome(dto.clienteNome());
        }
        if (dto.telefone() != null) {
            p.setTelefone(dto.telefone());
        }
        if (dto.tipo() != null) {
            p.setTipo(dto.tipo());
        }
        if (dto.formaPagamento() != null) {
            p.setFormaPagamento(dto.formaPagamento());
        }
        if (dto.enderecoEntrega() != null) {
            p.setEnderecoEntrega(dto.enderecoEntrega());
        }
        if (dto.itens() != null && !dto.itens().isEmpty()) {
            validarItens(dto.itens());
            p.getItens().clear();
            aplicarItens(p, dto.itens());
        }
        BigDecimal taxa = p.getTipo() == TipoPedido.ENTREGA
                ? (dto.taxaEntrega() != null ? dto.taxaEntrega() : BigDecimal.ZERO)
                : BigDecimal.ZERO;
        if (p.getTipo() == TipoPedido.ENTREGA) {
            Entrega e = entregaRepository.findByPedido_Id(p.getId()).orElseGet(() -> {
                Entrega n = new Entrega();
                n.setPedido(p);
                n.setStatus(StatusEntrega.PENDENTE);
                return n;
            });
            e.setTaxaEntrega(taxa);
            if (dto.entregadorNome() != null) {
                e.setEntregadorNome(dto.entregadorNome());
            }
            entregaRepository.save(e);
        }
        p.setTotal(calcularTotalItens(p).add(taxa));
        return pedidoRepository.save(p);
    }

    @Transactional
    public Pedido mudarStatus(Long id, StatusPedido novo, Usuario usuario) {
        Pedido p = buscar(id);
        StatusPedido atual = p.getStatus();
        if (atual == novo) {
            return p;
        }
        if (atual == StatusPedido.CANCELADO) {
            throw new IllegalStateException("Pedido cancelado não pode ser alterado.");
        }
        if (novo == StatusPedido.CANCELADO && p.isEstoqueBaixado()) {
            estoqueService.estornarPorPedido(p, usuario);
            p.setEstoqueBaixado(false);
        }
        if (novo == StatusPedido.ENTREGUE) {
            if (!p.isEstoqueBaixado()) {
                estoqueService.baixarPorPedido(p, usuario);
                p.setEstoqueBaixado(true);
            }
            p.setStatus(StatusPedido.ENTREGUE);
            caixaSessaoService.registrarVendaPedido(p);
        } else {
            p.setStatus(novo);
        }
        if (p.getTipo() == TipoPedido.ENTREGA) {
            Entrega e = obterOuCriarEntrega(p);
            if (novo == StatusPedido.SAIU_ENTREGA) {
                e.setStatus(StatusEntrega.EM_ROTA);
                e.setHorarioSaida(Instant.now());
            }
            if (novo == StatusPedido.ENTREGUE) {
                e.setStatus(StatusEntrega.CONCLUIDA);
                e.setHorarioEntrega(Instant.now());
            }
            if (novo == StatusPedido.CANCELADO) {
                e.setStatus(StatusEntrega.CANCELADA);
            }
            entregaRepository.save(e);
        }
        return pedidoRepository.save(p);
    }

    private Entrega obterOuCriarEntrega(Pedido p) {
        return entregaRepository
                .findByPedido_Id(p.getId())
                .orElseGet(() -> {
                    Entrega n = new Entrega();
                    n.setPedido(p);
                    n.setStatus(StatusEntrega.PENDENTE);
                    BigDecimal taxa = p.getTotal().subtract(calcularTotalItens(p));
                    if (taxa.compareTo(BigDecimal.ZERO) < 0) {
                        taxa = BigDecimal.ZERO;
                    }
                    n.setTaxaEntrega(taxa);
                    return entregaRepository.save(n);
                });
    }

    private void garantirEditavel(Pedido p) {
        if (p.getStatus() == StatusPedido.ENTREGUE || p.getStatus() == StatusPedido.CANCELADO) {
            throw new IllegalStateException("Pedido não pode ser editado neste status.");
        }
    }

    private void validarItens(List<PedidoItemRequest> itens) {
        if (itens == null || itens.isEmpty()) {
            throw new IllegalArgumentException("Pedido precisa ter itens.");
        }
    }

    private void aplicarItens(Pedido p, List<PedidoItemRequest> itens) {
        for (PedidoItemRequest r : itens) {
            if (r.quantidade() <= 0) {
                throw new IllegalArgumentException("Quantidade inválida.");
            }
            boolean temProduto = r.produtoId() != null;
            boolean temCombo = r.comboId() != null;
            if (temProduto == temCombo) {
                throw new IllegalArgumentException("Cada item deve referenciar um produto OU um combo.");
            }
            if (temCombo) {
                p.getItens().add(montarItemCombo(p, r));
            } else {
                p.getItens().add(montarItemProduto(p, r));
            }
        }
    }

    private PedidoItem montarItemProduto(Pedido p, PedidoItemRequest r) {
        Produto prod = produtoRepository
                .findById(r.produtoId())
                .orElseThrow(() -> new IllegalArgumentException("Produto inválido."));
        if (!prod.isAtivo()) {
            throw new IllegalArgumentException("Produto inativo: " + prod.getNome());
        }
        boolean vendaUnidade = Boolean.TRUE.equals(r.vendaUnidade());
        if (vendaUnidade && !prod.permiteVendaUnidade()) {
            throw new IllegalArgumentException(
                    "Produto sem preço de unidade cadastrado: " + prod.getNome());
        }
        PedidoItem pi = new PedidoItem();
        pi.setPedido(p);
        pi.setProduto(prod);
        pi.setQuantidade(r.quantidade());
        pi.setVendaUnidade(vendaUnidade);
        if (vendaUnidade) {
            pi.setDescricao(prod.getNome() + " (unidade)");
            pi.setPrecoUnitario(prod.getPrecoUnidade());
            pi.setCustoUnitario(custoPorUnidade(prod));
        } else {
            pi.setPrecoUnitario(prod.getPreco());
            pi.setCustoUnitario(custoOuZero(prod.getCusto()));
        }
        return pi;
    }

    private PedidoItem montarItemCombo(Pedido p, PedidoItemRequest r) {
        Combo combo = comboRepository
                .findByIdWithItens(r.comboId())
                .orElseThrow(() -> new IllegalArgumentException("Combo inválido."));
        if (!combo.isAtivo()) {
            throw new IllegalArgumentException("Combo inativo: " + combo.getNome());
        }
        if (combo.getItens().isEmpty()) {
            throw new IllegalArgumentException("Combo sem produtos: " + combo.getNome());
        }
        PedidoItem pi = new PedidoItem();
        pi.setPedido(p);
        pi.setCombo(combo);
        pi.setDescricao(combo.getNome());
        pi.setQuantidade(r.quantidade());
        pi.setPrecoUnitario(combo.getPrecoVenda());
        pi.setCustoUnitario(custoTotalCombo(combo));
        return pi;
    }

    private static BigDecimal custoTotalCombo(Combo combo) {
        return combo.getItens().stream()
                .map(ci -> custoOuZero(ci.getProduto() != null ? ci.getProduto().getCusto() : null)
                        .multiply(BigDecimal.valueOf(ci.getQuantidade())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal custoOuZero(BigDecimal custo) {
        return custo != null ? custo : BigDecimal.ZERO;
    }

    private static BigDecimal custoPorUnidade(Produto prod) {
        BigDecimal custoPack = custoOuZero(prod.getCusto());
        int upe = prod.getUnidadesPorEmbalagem() != null ? prod.getUnidadesPorEmbalagem() : 1;
        if (upe <= 1) {
            return custoPack;
        }
        return custoPack.divide(BigDecimal.valueOf(upe), 2, java.math.RoundingMode.HALF_UP);
    }

    private void aplicarClienteNoPedido(Pedido p, PedidoRequest dto) {
        String nome = dto.clienteNome() != null ? dto.clienteNome().trim() : null;
        String telefone = dto.telefone() != null ? dto.telefone().trim() : null;
        if (dto.clienteId() != null) {
            Cliente c = clienteRepository
                    .findById(dto.clienteId())
                    .orElseThrow(() -> new IllegalArgumentException("Cliente não encontrado."));
            if (!c.isAtivo()) {
                throw new IllegalArgumentException("Cliente inativo.");
            }
            p.setCliente(c);
            if (nome == null || nome.isBlank()) {
                nome = c.getNome();
            }
            if (telefone == null || telefone.isBlank()) {
                telefone = c.getTelefone();
            }
        }
        p.setClienteNome(nome != null && !nome.isBlank() ? nome : null);
        p.setTelefone(telefone != null && !telefone.isBlank() ? telefone : null);
    }

    private BigDecimal calcularTotalItens(Pedido p) {
        return p.getItens().stream()
                .map(i -> i.getPrecoUnitario().multiply(BigDecimal.valueOf(i.getQuantidade())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
