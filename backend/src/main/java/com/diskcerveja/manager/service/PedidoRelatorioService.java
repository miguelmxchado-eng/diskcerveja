package com.diskcerveja.manager.service;

import com.diskcerveja.manager.domain.entity.Pedido;
import com.diskcerveja.manager.domain.entity.PedidoItem;
import com.diskcerveja.manager.domain.enums.PeriodoPedido;
import com.diskcerveja.manager.domain.enums.StatusPedido;
import com.diskcerveja.manager.dto.PedidoItemResponse;
import com.diskcerveja.manager.dto.PedidoMapper;
import com.diskcerveja.manager.dto.PedidoPeriodoResponse;
import com.diskcerveja.manager.dto.PedidoResumoDto;
import com.diskcerveja.manager.repository.MovimentoCaixaRepository;
import com.diskcerveja.manager.repository.PedidoRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PedidoRelatorioService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.forLanguageTag("pt-BR"));

    private final PedidoRepository pedidoRepository;
    private final MovimentoCaixaRepository movimentoCaixaRepository;

    public PedidoRelatorioService(
            PedidoRepository pedidoRepository, MovimentoCaixaRepository movimentoCaixaRepository) {
        this.pedidoRepository = pedidoRepository;
        this.movimentoCaixaRepository = movimentoCaixaRepository;
    }

    @Transactional(readOnly = true)
    public PedidoPeriodoResponse listarPorPeriodo(PeriodoPedido periodo) {
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
        }

        long quantidadeDias = ChronoUnit.DAYS.between(inicioD, fimD) + 1;
        Instant ini = inicioD.atStartOfDay(z).toInstant();
        Instant fim = fimD.plusDays(1).atStartOfDay(z).toInstant();

        List<Pedido> pedidos = pedidoRepository.findByDataHoraBetweenWithItens(ini, fim).stream()
                .collect(Collectors.toMap(Pedido::getId, p -> p, (a, b) -> a, LinkedHashMap::new))
                .values()
                .stream()
                .sorted(Comparator.comparing(Pedido::getDataHora).reversed())
                .collect(Collectors.toCollection(ArrayList::new));
        Set<Long> comCaixa = new HashSet<>();

        List<PedidoResumoDto> dtos = pedidos.stream()
                .map(p -> {
                    BigDecimal custo = custoDosItens(p);
                    BigDecimal lucro = p.getStatus() == StatusPedido.ENTREGUE
                            ? nvl(p.getTotal()).subtract(custo)
                            : null;
                    List<PedidoItemResponse> itens = p.getItens() == null
                            ? List.of()
                            : p.getItens().stream().map(PedidoMapper::toItem).toList();
                    return new PedidoResumoDto(
                            p.getId(),
                            p.getDataHora(),
                            p.getClienteNome(),
                            p.getTelefone(),
                            p.getTipo(),
                            p.getStatus(),
                            p.getTotal(),
                            nvl(p.getDesconto()),
                            custo,
                            lucro,
                            p.getFormaPagamento(),
                            p.getStatus() == StatusPedido.ENTREGUE && comCaixa.contains(p.getId()),
                            itens);
                })
                .toList();

        BigDecimal somaTodos = pedidos.stream().map(Pedido::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<Pedido> entregues = pedidos.stream()
                .filter(p -> p.getStatus() == StatusPedido.ENTREGUE)
                .toList();
        BigDecimal somaEntregues = entregues.stream()
                .map(Pedido::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal somaCustoEntregues = entregues.stream()
                .map(PedidoRelatorioService::custoDosItens)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal somaLucroEntregues = somaEntregues.subtract(somaCustoEntregues);
        BigDecimal margem = margemPercentual(somaLucroEntregues, somaEntregues);
        int semCaixa = (int) entregues.stream()
                .filter(p -> !comCaixa.contains(p.getId()))
                .count();

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
                somaTodos,
                somaEntregues,
                somaCustoEntregues,
                somaLucroEntregues,
                margem,
                semCaixa);
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
