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
import com.diskcerveja.manager.dto.PedidoPagamentoResponse;
import com.diskcerveja.manager.dto.PedidoPeriodoDiaDto;
import com.diskcerveja.manager.dto.PedidoPeriodoPagamentoDto;
import com.diskcerveja.manager.dto.PedidoPeriodoResponse;
import com.diskcerveja.manager.dto.PedidoPeriodoTopProdutoDto;
import com.diskcerveja.manager.dto.PedidoResumoDto;
import com.diskcerveja.manager.dto.PdvInsightsResponse;
import com.diskcerveja.manager.dto.ProjecaoMensalResponse;
import com.diskcerveja.manager.dto.ProdutoSugestaoDto;
import com.diskcerveja.manager.dto.ProdutoVendaRankDto;
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
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
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
                    List<PedidoPagamentoResponse> pagamentos = full.getPagamentos() == null
                            ? List.of()
                            : full.getPagamentos().stream()
                                    .map(pg -> {
                                        BigDecimal recebido = pg.getValorRecebido();
                                        BigDecimal troco = recebido != null
                                                ? recebido.subtract(pg.getValor()).max(BigDecimal.ZERO)
                                                : BigDecimal.ZERO;
                                        return new PedidoPagamentoResponse(
                                                pg.getFormaPagamento(),
                                                pg.getValor(),
                                                recebido,
                                                troco);
                                    })
                                    .toList();
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
                            itens,
                            pagamentos);
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
            case MISTO -> "Pagamento dividido";
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

    @Transactional(readOnly = true)
    public ProjecaoMensalResponse projecaoMensal() {
        var z = CaixaSessaoService.ZONA_OPERACAO;
        LocalDate hoje = LocalDate.now(z);
        LocalDate inicioMes = hoje.withDayOfMonth(1);
        LocalDate fimMes = hoje.with(TemporalAdjusters.lastDayOfMonth());
        int diasNoMes = fimMes.getDayOfMonth();
        int diasDecorridos = Math.max(1, hoje.getDayOfMonth());
        int diasRestantes = Math.max(0, diasNoMes - hoje.getDayOfMonth());
        int progressoMes = (int) Math.round((diasDecorridos * 100.0) / diasNoMes);

        Instant iniMes = inicioMes.atStartOfDay(z).toInstant();
        Instant fimHoje = hoje.plusDays(1).atStartOfDay(z).toInstant();

        BigDecimal faturamentoAtual = nvl(pedidoRepository.sumTotalEntreguesNoPeriodo(iniMes, fimHoje));
        BigDecimal custoAtual = nvl(pedidoRepository.sumCustoEntreguesNoPeriodo(iniMes, fimHoje));
        BigDecimal lucroAtual = faturamentoAtual.subtract(custoAtual);
        long pedidosAtual = pedidoRepository.countPedidosNoPeriodo(iniMes, fimHoje);

        Map<LocalDate, BigDecimal> porDia = new HashMap<>();
        for (Object[] row : pedidoRepository.aggregateVendasCancelamentosPorDiaOperacao(
                Timestamp.from(iniMes), Timestamp.from(fimHoje))) {
            porDia.put(toLocalDate(row[0]), nvl(row[1] instanceof BigDecimal bd ? bd : BigDecimal.ZERO));
        }

        BigDecimal somaUtil = BigDecimal.ZERO;
        BigDecimal somaFds = BigDecimal.ZERO;
        int diasUtil = 0;
        int diasFds = 0;
        for (LocalDate d = inicioMes; !d.isAfter(hoje); d = d.plusDays(1)) {
            BigDecimal v = porDia.getOrDefault(d, BigDecimal.ZERO);
            if (isFimDeSemana(d)) {
                somaFds = somaFds.add(v);
                diasFds++;
            } else {
                somaUtil = somaUtil.add(v);
                diasUtil++;
            }
        }

        BigDecimal mediaDiaria = dinheiro(faturamentoAtual.divide(
                BigDecimal.valueOf(diasDecorridos), 2, RoundingMode.HALF_UP));
        BigDecimal mediaUtil = diasUtil > 0
                ? dinheiro(somaUtil.divide(BigDecimal.valueOf(diasUtil), 2, RoundingMode.HALF_UP))
                : mediaDiaria;
        BigDecimal mediaFds = diasFds > 0
                ? dinheiro(somaFds.divide(BigDecimal.valueOf(diasFds), 2, RoundingMode.HALF_UP))
                : mediaDiaria;

        BigDecimal estimativaRestante = BigDecimal.ZERO;
        for (LocalDate d = hoje.plusDays(1); !d.isAfter(fimMes); d = d.plusDays(1)) {
            estimativaRestante = estimativaRestante.add(isFimDeSemana(d) ? mediaFds : mediaUtil);
        }
        estimativaRestante = dinheiro(estimativaRestante);

        BigDecimal realista = dinheiro(faturamentoAtual.add(estimativaRestante));
        BigDecimal pessimista = dinheiro(faturamentoAtual.add(
                estimativaRestante.multiply(new BigDecimal("0.85"))));
        BigDecimal otimista = dinheiro(faturamentoAtual.add(
                estimativaRestante.multiply(new BigDecimal("1.15"))));

        BigDecimal lucroProjetado = BigDecimal.ZERO;
        long pedidosProjetados = pedidosAtual;
        if (faturamentoAtual.compareTo(BigDecimal.ZERO) > 0) {
            lucroProjetado = dinheiro(lucroAtual
                    .multiply(realista)
                    .divide(faturamentoAtual, 4, RoundingMode.HALF_UP));
            pedidosProjetados = Math.round(
                    pedidosAtual * realista.divide(faturamentoAtual, 4, RoundingMode.HALF_UP).doubleValue());
        } else if (diasRestantes > 0) {
            pedidosProjetados = pedidosAtual;
        }

        LocalDate anoPassadoInicio = inicioMes.minusYears(1);
        LocalDate anoPassadoHoje = hoje.minusYears(1);
        LocalDate anoPassadoFim = fimMes.minusYears(1);
        Instant apIni = anoPassadoInicio.atStartOfDay(z).toInstant();
        Instant apHoje = anoPassadoHoje.plusDays(1).atStartOfDay(z).toInstant();
        Instant apFim = anoPassadoFim.plusDays(1).atStartOfDay(z).toInstant();
        BigDecimal mesmoPeriodoAnoPassado = nvl(pedidoRepository.sumTotalEntreguesNoPeriodo(apIni, apHoje));
        BigDecimal mesmoMesAnoPassado = nvl(pedidoRepository.sumTotalEntreguesNoPeriodo(apIni, apFim));

        LocalDate mesAntInicio = inicioMes.minusMonths(1);
        LocalDate mesAntFim = inicioMes.minusDays(1);
        Instant maIni = mesAntInicio.atStartOfDay(z).toInstant();
        Instant maFim = mesAntFim.plusDays(1).atStartOfDay(z).toInstant();
        BigDecimal mesAnterior = nvl(pedidoRepository.sumTotalEntreguesNoPeriodo(maIni, maFim));

        BigDecimal metaMensal;
        String metaOrigem;
        if (mesmoMesAnoPassado.compareTo(BigDecimal.ZERO) > 0) {
            metaMensal = dinheiro(mesmoMesAnoPassado);
            metaOrigem = "mesmo mês do ano passado";
        } else if (mesAnterior.compareTo(BigDecimal.ZERO) > 0) {
            metaMensal = dinheiro(mesAnterior);
            metaOrigem = "mês anterior";
        } else {
            metaMensal = realista;
            metaOrigem = "ritmo atual do mês";
        }

        BigDecimal faltaParaMeta = dinheiro(metaMensal.subtract(faturamentoAtual).max(BigDecimal.ZERO));
        BigDecimal faltaPorDia = diasRestantes > 0
                ? dinheiro(faltaParaMeta.divide(BigDecimal.valueOf(diasRestantes), 2, RoundingMode.HALF_UP))
                : BigDecimal.ZERO;
        boolean noRitmo = realista.compareTo(metaMensal) >= 0;

        return new ProjecaoMensalResponse(
                diasDecorridos,
                diasRestantes,
                diasNoMes,
                progressoMes,
                dinheiro(faturamentoAtual),
                dinheiro(lucroAtual),
                pedidosAtual,
                mediaDiaria,
                mediaUtil,
                mediaFds,
                estimativaRestante,
                pessimista,
                realista,
                otimista,
                lucroProjetado,
                pedidosProjetados,
                metaMensal,
                metaOrigem,
                faltaParaMeta,
                faltaPorDia,
                noRitmo,
                dinheiro(mesmoMesAnoPassado),
                dinheiro(mesmoPeriodoAnoPassado),
                dinheiro(mesAnterior));
    }

    @Transactional(readOnly = true)
    public PdvInsightsResponse insightsPdv(int dias) {
        int janela = Math.min(90, Math.max(7, dias));
        var z = CaixaSessaoService.ZONA_OPERACAO;
        LocalDate hoje = LocalDate.now(z);
        Instant ini = hoje.minusDays(janela - 1L).atStartOfDay(z).toInstant();
        Instant fim = hoje.plusDays(1).atStartOfDay(z).toInstant();
        List<ProdutoVendaRankDto> ranking = new ArrayList<>();
        for (Object[] row : pedidoRepository.topProdutosComEstoque(
                Timestamp.from(ini), Timestamp.from(fim), 40)) {
            ranking.add(toRank(row));
        }
        List<ProdutoVendaRankDto> alertas = ranking.stream()
                .filter(ProdutoVendaRankDto::estoqueBaixo)
                .limit(6)
                .toList();
        return new PdvInsightsResponse(janela, ranking, alertas);
    }

    @Transactional(readOnly = true)
    public List<ProdutoSugestaoDto> sugestoesLevaJunto(Collection<Long> produtoIds, int limite) {
        if (produtoIds == null || produtoIds.isEmpty()) {
            return List.of();
        }
        Set<Long> ids = produtoIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return List.of();
        }
        int lim = Math.min(8, Math.max(1, limite));
        var z = CaixaSessaoService.ZONA_OPERACAO;
        LocalDate hoje = LocalDate.now(z);
        Instant ini = hoje.minusDays(60).atStartOfDay(z).toInstant();
        Instant fim = hoje.plusDays(1).atStartOfDay(z).toInstant();
        List<ProdutoSugestaoDto> out = new ArrayList<>();
        for (Object[] row : pedidoRepository.sugestoesLevaJunto(
                Timestamp.from(ini), Timestamp.from(fim), ids, lim)) {
            Long id = row[0] instanceof Number n ? n.longValue() : null;
            if (id == null) {
                continue;
            }
            String nome = row[1] != null ? row[1].toString() : "Produto";
            BigDecimal preco = row[2] instanceof BigDecimal bd ? bd : BigDecimal.ZERO;
            long vezes = row[3] instanceof Number n ? n.longValue() : 0L;
            int estoque = row[4] instanceof Number n ? n.intValue() : 0;
            out.add(new ProdutoSugestaoDto(id, nome, preco, vezes, estoque));
        }
        return out;
    }

    private static ProdutoVendaRankDto toRank(Object[] row) {
        Long id = row[0] instanceof Number n ? n.longValue() : null;
        String nome = row[1] != null ? row[1].toString() : "Produto";
        long un = row[2] instanceof Number n ? n.longValue() : 0L;
        BigDecimal valor = row[3] instanceof BigDecimal bd ? bd : BigDecimal.ZERO;
        int estoque = row[4] instanceof Number n ? n.intValue() : 0;
        int minimo = row[5] instanceof Number n ? n.intValue() : 0;
        boolean baixo = estoque <= 0 || estoque <= minimo;
        return new ProdutoVendaRankDto(id, nome, un, valor, estoque, minimo, baixo);
    }

    private static boolean isFimDeSemana(LocalDate d) {
        DayOfWeek dow = d.getDayOfWeek();
        return dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY;
    }

    private static BigDecimal dinheiro(BigDecimal v) {
        return nvl(v).setScale(2, RoundingMode.HALF_UP);
    }
}
