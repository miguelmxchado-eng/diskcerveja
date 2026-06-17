package com.diskcerveja.manager.service;

import com.diskcerveja.manager.domain.entity.Combo;
import com.diskcerveja.manager.domain.entity.ComboItem;
import com.diskcerveja.manager.domain.entity.Produto;
import com.diskcerveja.manager.domain.enums.StatusPedido;
import com.diskcerveja.manager.dto.ComboDto;
import com.diskcerveja.manager.dto.ComboItemDto;
import com.diskcerveja.manager.dto.ComboItemResponse;
import com.diskcerveja.manager.dto.ComboRelatorioResponse;
import com.diskcerveja.manager.dto.ComboResponse;
import com.diskcerveja.manager.dto.ComboVendaAgg;
import com.diskcerveja.manager.repository.ComboRepository;
import com.diskcerveja.manager.repository.PedidoItemRepository;
import com.diskcerveja.manager.repository.ProdutoRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ComboService {

    private final ComboRepository comboRepository;
    private final ProdutoRepository produtoRepository;
    private final PedidoItemRepository pedidoItemRepository;

    public ComboService(
            ComboRepository comboRepository,
            ProdutoRepository produtoRepository,
            PedidoItemRepository pedidoItemRepository) {
        this.comboRepository = comboRepository;
        this.produtoRepository = produtoRepository;
        this.pedidoItemRepository = pedidoItemRepository;
    }

    @Transactional(readOnly = true)
    public List<ComboResponse> listar(boolean somenteAtivos) {
        List<Combo> combos = somenteAtivos
                ? comboRepository.findAtivosWithItens()
                : comboRepository.findAllWithItens();
        Map<Long, ComboVendaAgg> vendas = mapaVendas();
        return combos.stream().map(c -> toResponse(c, vendas)).toList();
    }

    @Transactional(readOnly = true)
    public ComboResponse buscarResponse(Long id) {
        Combo c = buscar(id);
        return toResponse(c, mapaVendas());
    }

    @Transactional(readOnly = true)
    public Combo buscar(Long id) {
        return comboRepository
                .findByIdWithItens(id)
                .orElseThrow(() -> new IllegalArgumentException("Combo não encontrado."));
    }

    @Transactional(readOnly = true)
    public List<ComboRelatorioResponse> relatorio() {
        Map<Long, ComboVendaAgg> vendas = mapaVendas();
        return comboRepository.findAllWithItens().stream()
                .map(c -> {
                    ComboVendaAgg agg = vendas.get(c.getId());
                    long qtd = agg != null ? agg.quantidade() : 0L;
                    BigDecimal faturamento = agg != null ? agg.faturamento() : BigDecimal.ZERO;
                    BigDecimal custoTotal = custoTotal(c).multiply(BigDecimal.valueOf(qtd));
                    BigDecimal lucro = faturamento.subtract(custoTotal);
                    BigDecimal margem = faturamento.signum() > 0
                            ? lucro.multiply(BigDecimal.valueOf(100)).divide(faturamento, 2, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO;
                    return new ComboRelatorioResponse(
                            c.getId(), c.getNome(), qtd, faturamento, custoTotal, lucro, margem);
                })
                .sorted(Comparator.comparingLong(ComboRelatorioResponse::quantidadeVendida).reversed())
                .toList();
    }

    @Transactional
    public ComboResponse salvar(ComboDto dto) {
        if (dto.itens() == null || dto.itens().isEmpty()) {
            throw new IllegalArgumentException("O combo precisa de ao menos um produto.");
        }
        if (dto.precoVenda() == null || dto.precoVenda().signum() < 0) {
            throw new IllegalArgumentException("Preço de venda inválido.");
        }

        Combo combo = dto.id() == null ? new Combo() : buscar(dto.id());

        String codigoBarras = normalizar(dto.codigoBarras());
        String codigoQr = normalizar(dto.codigoQr());
        validarDuplicidade(codigoBarras, codigoQr, dto.id());

        combo.setNome(dto.nome().trim());
        combo.setCodigoBarras(codigoBarras);
        combo.setCodigoQr(codigoQr);
        combo.setCategoria(dto.categoria());
        combo.setDescricao(normalizar(dto.descricao()));
        combo.setImagem(dto.imagem());
        combo.setPrecoVenda(dto.precoVenda());
        combo.setAtivo(dto.ativo());
        if (combo.getCodigo() == null || combo.getCodigo().isBlank()) {
            combo.setCodigo(gerarCodigoInterno());
        }

        aplicarItens(combo, dto.itens());

        Combo salvo = comboRepository.save(combo);
        return toResponse(buscar(salvo.getId()), mapaVendas());
    }

    @Transactional
    public void excluir(Long id) {
        Combo c = buscar(id);
        // Soft delete: preserva integridade com pedidos já registrados.
        c.setAtivo(false);
        comboRepository.save(c);
    }

    private void aplicarItens(Combo combo, List<ComboItemDto> itens) {
        combo.getItens().clear();
        for (ComboItemDto it : itens) {
            if (it.quantidade() <= 0) {
                throw new IllegalArgumentException("Quantidade inválida no item do combo.");
            }
            Produto prod = produtoRepository
                    .findById(it.produtoId())
                    .orElseThrow(() -> new IllegalArgumentException("Produto do combo inválido."));
            if (!prod.isAtivo()) {
                throw new IllegalArgumentException("Produto inativo no combo: " + prod.getNome());
            }
            ComboItem ci = new ComboItem();
            ci.setCombo(combo);
            ci.setProduto(prod);
            ci.setQuantidade(it.quantidade());
            combo.getItens().add(ci);
        }
    }

    private void validarDuplicidade(String codigoBarras, String codigoQr, Long excluirId) {
        if (codigoBarras != null) {
            comboRepository.findConflitoCodigo(codigoBarras, excluirId).ifPresent(c -> {
                throw new IllegalStateException("Código de barras já usado no combo \"" + c.getNome() + "\".");
            });
        }
        if (codigoQr != null) {
            comboRepository.findConflitoCodigo(codigoQr, excluirId).ifPresent(c -> {
                throw new IllegalStateException("QR Code já usado no combo \"" + c.getNome() + "\".");
            });
        }
    }

    private Map<Long, ComboVendaAgg> mapaVendas() {
        Map<Long, ComboVendaAgg> map = new HashMap<>();
        for (ComboVendaAgg agg : pedidoItemRepository.agregarVendasCombosPorStatus(StatusPedido.ENTREGUE)) {
            if (agg.comboId() != null) {
                map.put(agg.comboId(), agg);
            }
        }
        return map;
    }

    private ComboResponse toResponse(Combo c, Map<Long, ComboVendaAgg> vendas) {
        BigDecimal custoTotal = custoTotal(c);
        BigDecimal lucro = c.getPrecoVenda().subtract(custoTotal);
        BigDecimal margem = c.getPrecoVenda().signum() > 0
                ? lucro.multiply(BigDecimal.valueOf(100)).divide(c.getPrecoVenda(), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        ComboVendaAgg agg = vendas.get(c.getId());
        long quantidadeVendida = agg != null ? agg.quantidade() : 0L;
        BigDecimal faturamento = agg != null ? agg.faturamento() : BigDecimal.ZERO;

        List<ComboItemResponse> itens = new ArrayList<>();
        int estoqueDisponivel = Integer.MAX_VALUE;
        for (ComboItem ci : c.getItens()) {
            Produto p = ci.getProduto();
            BigDecimal custoUnit = p.getCusto() != null ? p.getCusto() : BigDecimal.ZERO;
            int possiveis = ci.getQuantidade() > 0 ? p.getEstoqueAtual() / ci.getQuantidade() : 0;
            estoqueDisponivel = Math.min(estoqueDisponivel, possiveis);
            itens.add(new ComboItemResponse(
                    p.getId(),
                    p.getNome(),
                    ci.getQuantidade(),
                    custoUnit,
                    p.getPreco(),
                    p.getEstoqueAtual(),
                    p.isAtivo()));
        }
        if (c.getItens().isEmpty()) {
            estoqueDisponivel = 0;
        }

        return new ComboResponse(
                c.getId(),
                c.getNome(),
                c.getCodigo(),
                c.getCodigoBarras(),
                c.getCodigoQr(),
                c.getCategoria(),
                c.getDescricao(),
                c.getImagem(),
                c.getPrecoVenda(),
                c.isAtivo(),
                custoTotal,
                lucro,
                margem,
                quantidadeVendida,
                faturamento,
                estoqueDisponivel,
                itens);
    }

    private BigDecimal custoTotal(Combo c) {
        return c.getItens().stream()
                .map(ci -> {
                    BigDecimal custo = ci.getProduto().getCusto() != null
                            ? ci.getProduto().getCusto()
                            : BigDecimal.ZERO;
                    return custo.multiply(BigDecimal.valueOf(ci.getQuantidade()));
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private String gerarCodigoInterno() {
        String codigo;
        do {
            codigo = "CB-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
        } while (comboRepository.existsByCodigo(codigo));
        return codigo;
    }

    private static String normalizar(String v) {
        if (v == null || v.isBlank()) {
            return null;
        }
        return v.trim();
    }
}
