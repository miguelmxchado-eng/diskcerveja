package com.diskcerveja.manager.service;

import com.diskcerveja.manager.domain.enums.FormaPagamento;
import com.diskcerveja.manager.domain.enums.StatusPedido;
import com.diskcerveja.manager.dto.DashboardResponse;
import com.diskcerveja.manager.dto.DashboardResponse.ResumoCaixaHoje;
import com.diskcerveja.manager.dto.PontoGraficoVendas;
import com.diskcerveja.manager.repository.PedidoRepository;
import com.diskcerveja.manager.repository.ProdutoRepository;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class DashboardService {

    private static final Locale PT = Locale.forLanguageTag("pt-BR");
    private static final DateTimeFormatter MES_CURTO = DateTimeFormatter.ofPattern("MMM/yy", PT);

    private final PedidoRepository pedidoRepository;
    private final ProdutoRepository produtoRepository;
    private final CaixaSessaoService caixaSessaoService;

    public DashboardService(
            PedidoRepository pedidoRepository,
            ProdutoRepository produtoRepository,
            CaixaSessaoService caixaSessaoService) {
        this.pedidoRepository = pedidoRepository;
        this.produtoRepository = produtoRepository;
        this.caixaSessaoService = caixaSessaoService;
    }

    public DashboardResponse resumo() {
        ZoneId z = CaixaSessaoService.ZONA_OPERACAO;
        LocalDate hoje = LocalDate.now(z);
        ZonedDateTime inicio = hoje.atStartOfDay(z);
        ZonedDateTime fim = hoje.plusDays(1).atStartOfDay(z);
        Instant i0 = inicio.toInstant();
        Instant i1 = fim.toInstant();

        BigDecimal vendas = pedidoRepository.sumTotalEntreguesNoPeriodo(i0, i1);

        LocalDate ontem = hoje.minusDays(1);
        Instant o0 = ontem.atStartOfDay(z).toInstant();
        Instant o1 = ontem.plusDays(1).atStartOfDay(z).toInstant();
        BigDecimal vendasOntem = pedidoRepository.sumTotalEntreguesNoPeriodo(o0, o1);

        long cancelamentosHoje =
                pedidoRepository.countStatusNoPeriodo(StatusPedido.CANCELADO, i0, i1);

        long emAndamento = pedidoRepository.countByStatusIn(
                List.of(StatusPedido.ABERTO, StatusPedido.EM_PREPARO, StatusPedido.SAIU_ENTREGA));
        Instant limiteAtraso = Instant.now().minusSeconds(45 * 60);
        long atrasados = pedidoRepository.countAtrasados(
                List.of(StatusPedido.ABERTO, StatusPedido.EM_PREPARO, StatusPedido.SAIU_ENTREGA), limiteAtraso);
        long baixo = produtoRepository.findBaixoEstoque().size();

        var sessao = caixaSessaoService.sessaoAbertaHoje();
        Map<FormaPagamento, BigDecimal> porForma = new EnumMap<>(FormaPagamento.class);
        for (FormaPagamento fp : FormaPagamento.values()) {
            porForma.put(fp, BigDecimal.ZERO);
        }
        for (var row : pedidoRepository.sumByFormaPagamento(i0, i1)) {
            porForma.put(row.getForma(), row.getTotal());
        }
        ResumoCaixaHoje caixa;
        if (sessao != null) {
            caixa = new ResumoCaixaHoje(
                    true,
                    sessao.getValorAbertura(),
                    caixaSessaoService.saldoPrevisto(sessao),
                    Map.copyOf(porForma));
        } else {
            caixa = new ResumoCaixaHoje(false, BigDecimal.ZERO, BigDecimal.ZERO, Map.copyOf(porForma));
        }

        List<PontoGraficoVendas> graficoDiario = buildGraficoDiario(hoje, z);
        List<PontoGraficoVendas> graficoSemanal = buildGraficoSemanal(hoje, z);
        List<PontoGraficoVendas> graficoMensal = buildGraficoMensal(hoje, z);

        return new DashboardResponse(
                vendas,
                vendasOntem,
                cancelamentosHoje,
                emAndamento,
                atrasados,
                baixo,
                caixa,
                graficoDiario,
                graficoSemanal,
                graficoMensal);
    }

    private List<PontoGraficoVendas> buildGraficoDiario(LocalDate hoje, ZoneId z) {
        LocalDate primeiro = hoje.minusDays(6);
        Instant ini = primeiro.atStartOfDay(z).toInstant();
        Instant fim = hoje.plusDays(1).atStartOfDay(z).toInstant();
        Map<LocalDate, BigDecimal> vendas = new HashMap<>();
        Map<LocalDate, Long> cancel = new HashMap<>();
        for (Object[] row : pedidoRepository.aggregateVendasCancelamentosPorDiaOperacao(
                Timestamp.from(ini), Timestamp.from(fim))) {
            LocalDate d = toLocalDate(row[0], z);
            vendas.put(d, (BigDecimal) row[1]);
            cancel.put(d, ((Number) row[2]).longValue());
        }
        List<LocalDate> dias = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            dias.add(primeiro.plusDays(i));
        }
        return montarPontosDias(dias, vendas, cancel, PT, true);
    }

    private List<PontoGraficoVendas> buildGraficoSemanal(LocalDate hoje, ZoneId z) {
        LocalDate domingo = hoje.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));
        Instant ini = domingo.atStartOfDay(z).toInstant();
        Instant fim = domingo.plusDays(7).atStartOfDay(z).toInstant();
        Map<LocalDate, BigDecimal> vendas = new HashMap<>();
        Map<LocalDate, Long> cancel = new HashMap<>();
        for (Object[] row : pedidoRepository.aggregateVendasCancelamentosPorDiaOperacao(
                Timestamp.from(ini), Timestamp.from(fim))) {
            LocalDate d = toLocalDate(row[0], z);
            vendas.put(d, (BigDecimal) row[1]);
            cancel.put(d, ((Number) row[2]).longValue());
        }
        List<LocalDate> dias = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            dias.add(domingo.plusDays(i));
        }
        return montarPontosDias(dias, vendas, cancel, Locale.ENGLISH, false);
    }

    private List<PontoGraficoVendas> buildGraficoMensal(LocalDate hoje, ZoneId z) {
        YearMonth fimYm = YearMonth.from(hoje);
        YearMonth iniYm = fimYm.minusMonths(6);
        Instant ini = iniYm.atDay(1).atStartOfDay(z).toInstant();
        Instant fim = hoje.plusDays(1).atStartOfDay(z).toInstant();
        Map<YearMonth, BigDecimal> vendas = new HashMap<>();
        Map<YearMonth, Long> cancel = new HashMap<>();
        for (Object[] row : pedidoRepository.aggregateVendasCancelamentosPorMesOperacao(
                Timestamp.from(ini), Timestamp.from(fim))) {
            YearMonth ym = YearMonth.from(toLocalDate(row[0], z));
            vendas.put(ym, (BigDecimal) row[1]);
            cancel.put(ym, ((Number) row[2]).longValue());
        }
        List<PontoGraficoVendas> out = new ArrayList<>();
        for (YearMonth ym = iniYm; !ym.isAfter(fimYm); ym = ym.plusMonths(1)) {
            BigDecimal v = vendas.getOrDefault(ym, BigDecimal.ZERO);
            long c = cancel.getOrDefault(ym, 0L);
            String rotulo = ym.format(MES_CURTO).replace(".", "");
            String completo = ym.getMonth().getDisplayName(TextStyle.FULL, PT) + " " + ym.getYear();
            out.add(new PontoGraficoVendas(rotulo, completo, v, c));
        }
        return out;
    }

    private static List<PontoGraficoVendas> montarPontosDias(
            List<LocalDate> diasOrdenados,
            Map<LocalDate, BigDecimal> vendas,
            Map<LocalDate, Long> cancel,
            Locale localeRotulos,
            boolean incluirDataNoCompleto) {
        List<PontoGraficoVendas> out = new ArrayList<>();
        for (LocalDate d : diasOrdenados) {
            BigDecimal v = vendas.getOrDefault(d, BigDecimal.ZERO);
            long c = cancel.getOrDefault(d, 0L);
            String diaSemana =
                    d.getDayOfWeek().getDisplayName(TextStyle.FULL, localeRotulos);
            String rotulo = diaSemana;
            String completo = incluirDataNoCompleto
                    ? diaSemana + " (" + d.format(DateTimeFormatter.ofPattern("dd/MM/yyyy", PT)) + ")"
                    : diaSemana;
            out.add(new PontoGraficoVendas(rotulo, completo, v, c));
        }
        return out;
    }

    /**
     * Converte o primeiro campo retornado por agregações SQL nativas. Hibernate 6 / drivers recentes
     * podem mapear timestamp como {@link Instant} em vez de {@link Timestamp}.
     */
    private static LocalDate toLocalDate(Object o, ZoneId zonaOperacao) {
        if (o == null) {
            throw new IllegalArgumentException("Data nula no resultado da agregação");
        }
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
            return jud.toInstant().atZone(zonaOperacao).toLocalDate();
        }
        if (o instanceof Instant ins) {
            return ins.atZone(zonaOperacao).toLocalDate();
        }
        if (o instanceof LocalDateTime ldt) {
            return ldt.atZone(zonaOperacao).toLocalDate();
        }
        if (o instanceof ZonedDateTime zdt) {
            return zdt.withZoneSameInstant(zonaOperacao).toLocalDate();
        }
        if (o instanceof OffsetDateTime odt) {
            return odt.atZoneSameInstant(zonaOperacao).toLocalDate();
        }
        throw new IllegalArgumentException(
                "Tipo de data inesperado: " + o.getClass().getName());
    }
}
