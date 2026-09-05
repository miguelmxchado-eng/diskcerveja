package com.diskcerveja.manager.service;

import com.diskcerveja.manager.domain.entity.Pedido;
import com.diskcerveja.manager.domain.entity.PedidoItem;
import com.diskcerveja.manager.domain.enums.FormaPagamento;
import com.diskcerveja.manager.domain.enums.PeriodoPedido;
import com.diskcerveja.manager.domain.enums.StatusPedido;
import com.diskcerveja.manager.domain.enums.TipoMovimentoCaixa;
import com.diskcerveja.manager.domain.enums.TipoPedido;
import com.diskcerveja.manager.dto.FormaPagamentoAgg;
import com.diskcerveja.manager.dto.PedidoItemResponse;
import com.diskcerveja.manager.dto.PedidoMapper;
import com.diskcerveja.manager.dto.PedidoPeriodoDiaDto;
import com.diskcerveja.manager.dto.PedidoPeriodoPagamentoDto;
import com.diskcerveja.manager.dto.PedidoPeriodoResponse;
import com.diskcerveja.manager.dto.PedidoPeriodoTopProdutoDto;
import com.diskcerveja.manager.dto.PedidoResumoDto;
import com.diskcerveja.manager.repository.MovimentoCaixaRepository;
import com.diskcerveja.manager.repository.PedidoRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PedidoRelatorioService {

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.forLanguageTag("pt-BR"));
    private static final DateTimeFormatter DIA_ROTULO =
            DateTimeFormatter.ofPattern("dd/MM", Locale.forLanguageTag("pt-BR"));
    private static final int MAX_PAGE_SIZE = 100;

    private final PedidoRepository pedidoRepository;
    private final MovimentoCaixaRepository movimentoCaixaRepository;

    public PedidoRelatorioService(
            PedidoRepository pedidoRepository, MovimentoCaixaRepository movimentoCaixaRepository) {
        this.pedidoRepository = pedidoRepository;
        this.movimentoCaixaRepository = movimentoCaixaRepository;
    }

    @Transactional(readOnly = true)
    public PedidoPeriodoResponse listarPorPeriodo(
            PeriodoPedido periodo,
            int pagina,
            int tamanho,
            String q,
            StatusPedido status,
            TipoPedido tipo,
            FormaPagamento pagamento) {
        var z = CaixaSessaoService.ZONA_OPERACAO;
        LocalDate hoje = LocalDate.now(z);
        LocalDate inicioD = hoje;
        LocalDate fimD = hoje;
        switch (periodo) {
            case DIA -> {
                inicioD = hoje;
                fimD = hoje;
            }
            case SEMANA -> {
                inicioD = hoje.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));
                fimD = inicioD.plusDays(6);
            }
            case MES -> {
                inicioD = hoje.withDayOfMonth(1);
                fimD = hoje.with(TemporalAdjusters.lastDayOfMonth());
            }
            case ANO -> {
                inicioD = LocalDate.of(hoje.getYear(), 1, 1);
                fimD = LocalDate.of(hoje.getYear(), 12, 31);
            }
            case PERSONALIZADO -> throw new IllegalArgumentException(
                    "Para período personalizado informe as datas de início e fim.");
        }
        return montarResposta(periodo, inicioD, fimD, pagina, tamanho, q, status, tipo, pagamento);
    }

    @Transactional(readOnly = true)
    public PedidoPeriodoResponse listarPorIntervalo(
            LocalDate inicio,
            LocalDate fim,
            int pagina,
            int tamanho,
            String q,
            StatusPedido status,
            TipoPedido tipo,
            FormaPagamento pagamento) {
        if (inicio == null || fim == null) {
            throw new IllegalArgumentException("Informe a data inicial e a data final.");
        }
        if (fim.isBefore(inicio)) {
            throw new IllegalArgumentException("A data final não pode ser antes da data inicial.");
        }
        long dias = ChronoUnit.DAYS.between(inicio, fim) + 1;
        if (dias > 366) {
            throw new IllegalArgumentException("O intervalo máximo é de 366 dias.");
        }
        return montarResposta(
                PeriodoPedido.PERSONALIZADO, inicio, fim, pagina, tamanho, q, status, tipo, pagamento);
    }

    private PedidoPeriodoResponse montarResposta(
            PeriodoPedido periodo,
            LocalDate inicioD,
            LocalDate fimD,
            int pagina,
            int tamanho,
            String q,
            StatusPedido status,
            TipoPedido tipo,
            FormaPagamento pagamento) {
        var z = CaixaSessaoService.ZONA_OPERACAO;
        long quantidadeDias = ChronoUnit.DAYS.between(inicioD, fimD) + 1;
        Instant ini = inicioD.atStartOfDay(z).toInstant();
        Instant fim = fimD.plusDays(1).atStartOfDay(z).toInstant();

        int pageIndex = Math.max(0, pagina - 1);
        int pageSize = Math.min(MAX_PAGE_SIZE, Math.max(1, tamanho));
        String busca = q == null ? "" : q.trim();
        Long qId = parseId(busca);

        Page<Pedido> page = pedidoRepository.searchHistorico(
                ini,
                fim,
                status,
                tipo,
                pagamento,
                busca.isEmpty() ? null : busca,
                qId,
                PageRequest.of(pageIndex, pageSize, Sort.by(Sort.Direction.DESC, "dataHora")));

        List<Long> ids = page.getContent().stream().map(Pedido::getId).toList();
        Map<Long, Pedido> comItens = ids.isEmpty()
                ? Map.of()
                : pedidoRepository.findByIdInWithItens(ids).stream()
                        .collect(Collectors.toMap(Pedido::getId, Function.identity(), (a, b) -> a));

        Set<Long> comCaixa = ids.isEmpty()
                ? Set.of()
                : new HashSet<>(movimentoCaixaRepository.findPedidoIdsComEntradaVenda(
                        TipoMovimentoCaixa.ENTRADA_VENDA, ids));

        List<PedidoResumoDto> dtos = page.getContent().stream()
                .map(p -> {
                    Pedido full = comItens.getOrDefault(p.getId(), p);
                    BigDecimal custo = custoDosItens(full);
                    BigDecimal lucro = full.getStatus() == StatusPedido.ENTREGUE
                            ? nvl(full.getTotal()).subtract(custo)
                            : null;
                    List<PedidoItemResponse> itens = full.getItens() == null
                            ? List.of()
                            : full.getItens().stream().map(PedidoMapper::toItem).toList();
                    return new PedidoResumoDto(
                            full.getId(),
                            full.getDataHora(),
                            full.getClienteNome(),
                            full.getTelefone(),
                            full.getTipo(),
                            full.getStatus(),
                            full.getTotal(),
                            nvl(full.getDesconto()),
                            custo,
                            lucro,
                            full.getFormaPagamento(),
                            full.getStatus() == StatusPedido.ENTREGUE && comCaixa.contains(full.getId()),
                            itens);
                })
                .toList();

        BigDecimal somaTodos = nvl(pedidoRepository.sumTotalPedidosNoPeriodo(ini, fim));
        BigDecimal somaEntregues = nvl(pedidoRepository.sumTotalEntreguesNoPeriodo(ini, fim));
        BigDecimal somaCustoEntregues = nvl(pedidoRepository.sumCustoEntreguesNoPeriodo(ini, fim));
        BigDecimal somaLucroEntregues = somaEntregues.subtract(somaCustoEntregues);
        BigDecimal margem = margemPercentual(somaLucroEntregues, somaEntregues);
        int semCaixa = (int) pedidoRepository.countEntreguesSemCaixa(ini, fim);

        List<PedidoPeriodoDiaDto> faturamentoDiario = montarFaturamentoDiario(inicioD, fimD, ini, fim);
        List<PedidoPeriodoPagamentoDto> formas = montarFormasPagamento(ini, fim);
        List<PedidoPeriodoTopProdutoDto> tops = montarTopProdutos(ini, fim);

        LocalDate fimAnt = inicioD.minusDays(1);
        LocalDate iniAnt = fimAnt.minusDays(quantidadeDias - 1);
        Instant antIni = iniAnt.atStartOfDay(z).toInstant();
        Instant antFim = fimAnt.plusDays(1).atStartOfDay(z).toInstant();
        long pedidosAnt = pedidoRepository.countPedidosNoPeriodo(antIni, antFim);
        BigDecimal vendasAnt = nvl(pedidoRepository.sumTotalEntreguesNoPeriodo(antIni, antFim));
        BigDecimal custoAnt = nvl(pedidoRepository.sumCustoEntreguesNoPeriodo(antIni, antFim));
        BigDecimal lucroAnt = vendasAnt.subtract(custoAnt);
        BigDecimal margemAnt = margemPercentual(lucroAnt, vendasAnt);

        long pedidosPeriodo = pedidoRepository.countPedidosNoPeriodo(ini, fim);

        String desc = inicioD.equals(fimD)
                ? FMT.format(inicioD) + " (1 dia)"
                : FMT.format(inicioD) + " – " + FMT.format(fimD) + " (" + quantidadeDias + " dias)";

        return new PedidoPeriodoResponse(
                periodo,
                desc,
                inicioD,
                fimD,
                quantidadeDias,
                dtos,
                page.getTotalElements(),
                pedidosPeriodo,
                page.getNumber() + 1,
                page.getSize(),
                Math.max(1, page.getTotalPages()),
                somaTodos,
                somaEntregues,
                somaCustoEntregues,
                somaLucroEntregues,
                margem,
                semCaixa,
                faturamentoDiario,
                formas,
                tops,
                pedidosAnt,
                vendasAnt,
                lucroAnt,
                margemAnt);
    }

    private List<PedidoPeriodoDiaDto> montarFaturamentoDiario(
            LocalDate inicioD, LocalDate fimD, Instant ini, Instant fim) {
        Map<LocalDate, BigDecimal> porDia = new HashMap<>();
        for (Object[] row : pedidoRepository.aggregateVendasCancelamentosPorDiaOperacao(
                Timestamp.from(ini), Timestamp.from(fim))) {
            LocalDate dia = toLocalDate(row[0]);
            porDia.put(dia, (BigDecimal) row[1]);
        }
        List<PedidoPeriodoDiaDto> out = new ArrayList<>();
        for (LocalDate d = inicioD; !d.isAfter(fimD); d = d.plusDays(1)) {
            out.add(new PedidoPeriodoDiaDto(DIA_ROTULO.format(d), porDia.getOrDefault(d, BigDecimal.ZERO)));
        }
        return out;
    }

    private List<PedidoPeriodoPagamentoDto> montarFormasPagamento(Instant ini, Instant fim) {
        List<FormaPagamentoAgg> rows = pedidoRepository.sumByFormaPagamento(ini, fim);
        BigDecimal total = rows.stream()
                .map(FormaPagamentoAgg::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal base = total.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ONE : total;
        return rows.stream()
                .sorted(Comparator.comparing(FormaPagamentoAgg::getTotal).reversed())
                .map(r -> {
                    int pct = r.getTotal()
                            .multiply(BigDecimal.valueOf(100))
                            .divide(base, 0, RoundingMode.HALF_UP)
                            .intValue();
                    return new PedidoPeriodoPagamentoDto(labelPagamento(r.getForma()), r.getTotal(), pct);
                })
                .toList();
    }

    private List<PedidoPeriodoTopProdutoDto> montarTopProdutos(Instant ini, Instant fim) {
        List<PedidoPeriodoTopProdutoDto> out = new ArrayList<>();
        for (Object[] row :
                pedidoRepository.topProdutosEntregues(Timestamp.from(ini), Timestamp.from(fim))) {
            String nome = row[0] != null ? row[0].toString() : "Item";
            long un = row[1] instanceof Number n ? n.longValue() : 0L;
            BigDecimal valor = row[2] instanceof BigDecimal bd ? bd : BigDecimal.ZERO;
            out.add(new PedidoPeriodoTopProdutoDto(nome, un, valor));
        }
        return out;
    }

    private static LocalDate toLocalDate(Object o) {
        if (o instanceof LocalDate ld) {
            return ld;
        }
        if (o instanceof java.sql.Date d) {
            return d.toLocalDate();
        }
        if (o instanceof Timestamp t) {
            return t.toLocalDateTime().toLocalDate();
        }
        if (o instanceof java.util.Date jud) {
            return jud.toInstant().atZone(CaixaSessaoService.ZONA_OPERACAO).toLocalDate();
        }
        throw new IllegalArgumentException("Tipo de data inesperado: " + (o == null ? "null" : o.getClass()));
    }

    private static Long parseId(String q) {
        if (q == null || q.isBlank()) {
            return null;
        }
        String t = q.startsWith("#") ? q.substring(1).trim() : q.trim();
        try {
            return Long.parseLong(t);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String labelPagamento(FormaPagamento f) {
        if (f == null) {
            return "—";
        }
        return switch (f) {
            case PIX -> "PIX";
            case CARTAO -> "Cartão";
            case DINHEIRO -> "Dinheiro";
        };
    }

    private static BigDecimal custoDosItens(Pedido p) {
        if (p.getItens() == null || p.getItens().isEmpty()) {
            return BigDecimal.ZERO;
        }
        return p.getItens().stream()
                .map(PedidoRelatorioService::custoLinha)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal custoLinha(PedidoItem i) {
        return nvl(i.getCustoUnitario()).multiply(BigDecimal.valueOf(i.getQuantidade()));
    }

    private static BigDecimal margemPercentual(BigDecimal lucro, BigDecimal vendas) {
        if (vendas.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return lucro.multiply(BigDecimal.valueOf(100)).divide(vendas, 1, RoundingMode.HALF_UP);
    }

    private static BigDecimal nvl(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
