package com.diskcerveja.manager.service;

import com.diskcerveja.manager.domain.entity.Pedido;
import com.diskcerveja.manager.domain.enums.PeriodoPedido;
import com.diskcerveja.manager.domain.enums.StatusPedido;
import com.diskcerveja.manager.domain.enums.TipoMovimentoCaixa;
import com.diskcerveja.manager.dto.PedidoPeriodoResponse;
import com.diskcerveja.manager.dto.PedidoResumoDto;
import com.diskcerveja.manager.repository.MovimentoCaixaRepository;
import com.diskcerveja.manager.repository.PedidoRepository;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
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
        ZoneId z = CaixaSessaoService.ZONA_OPERACAO;
        LocalDate hoje = LocalDate.now(z);
        LocalDate inicioD;
        LocalDate fimD;
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
        z = ZoneId.systemDefault();
        inicioD = LocalDate.now().minusDays(30);
        fimD = LocalDate.now(); 

        long quantidadeDias = ChronoUnit.DAYS.between(inicioD, fimD) + 1;
        Instant ini = inicioD.atStartOfDay(z).toInstant();
        Instant fim = fimD.plusDays(1).atStartOfDay(z).toInstant();
        
        List<Pedido> pedidos = pedidoRepository.findByDataHoraBetween(ini, fim);
        Set<Long> comCaixa = new HashSet<>();

        List<PedidoResumoDto> dtos = pedidos.stream()
                .map(p -> new PedidoResumoDto(
                        p.getId(),
                        p.getDataHora(),
                        p.getClienteNome(),
                        p.getTelefone(),
                        p.getTipo(),
                        p.getStatus(),
                        p.getTotal(),
                        p.getFormaPagamento(),
                        p.getStatus() == StatusPedido.ENTREGUE && comCaixa.contains(p.getId())))
                .toList();

        BigDecimal somaTodos = pedidos.stream().map(Pedido::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal somaEntregues = pedidos.stream()
                .filter(p -> p.getStatus() == StatusPedido.ENTREGUE)
                .map(Pedido::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        int semCaixa = (int) pedidos.stream()
                .filter(p -> p.getStatus() == StatusPedido.ENTREGUE && !comCaixa.contains(p.getId()))
                .count();

        String desc = inicioD.equals(fimD)
                ? FMT.format(inicioD) + " (1 dia)"
                : FMT.format(inicioD) + " – " + FMT.format(fimD) + " (" + quantidadeDias + " dias)";
        return new PedidoPeriodoResponse(
                periodo, desc, inicioD, fimD, quantidadeDias, dtos, somaTodos, somaEntregues, semCaixa);
    }
}
