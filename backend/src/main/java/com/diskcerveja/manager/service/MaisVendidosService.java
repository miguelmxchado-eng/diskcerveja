package com.diskcerveja.manager.service;

import com.diskcerveja.manager.domain.enums.StatusPedido;
import com.diskcerveja.manager.dto.MaisVendidoItemDto;
import com.diskcerveja.manager.dto.MaisVendidosCategoriaDto;
import com.diskcerveja.manager.dto.MaisVendidosDiaDto;
import com.diskcerveja.manager.dto.MaisVendidosResponse;
import com.diskcerveja.manager.dto.MaisVendidosSerieResponse;
import com.diskcerveja.manager.repository.PedidoRepository;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MaisVendidosService {

    static final int MAX_DIAS = 731;

    private final PedidoRepository pedidoRepository;

    public MaisVendidosService(PedidoRepository pedidoRepository) {
        this.pedidoRepository = pedidoRepository;
    }

    @Transactional(readOnly = true)
    public MaisVendidosResponse ranking(LocalDate inicio, LocalDate fim) {
        validarIntervalo(inicio, fim);
        ZoneId z = CaixaSessaoService.ZONA_OPERACAO;
        long dias = ChronoUnit.DAYS.between(inicio, fim) + 1;
        LocalDate antFim = inicio.minusDays(1);
        LocalDate antIni = antFim.minusDays(dias - 1);

        Instant ini = inicio.atStartOfDay(z).toInstant();
        Instant end = fim.plusDays(1).atStartOfDay(z).toInstant();
        Instant aIni = antIni.atStartOfDay(z).toInstant();
        Instant aEnd = inicio.atStartOfDay(z).toInstant();

        List<Linha> atuais = carregarLinhas(ini, end);
        Map<String, Linha> anteriores = new HashMap<>();
        for (Linha l : carregarLinhas(aIni, aEnd)) {
            anteriores.put(l.chave, l);
        }

        long totalUn = 0;
        BigDecimal totalValor = BigDecimal.ZERO;
        BigDecimal totalCusto = BigDecimal.ZERO;
        Map<String, long[]> catUn = new LinkedHashMap<>();
        Map<String, BigDecimal> catValor = new HashMap<>();
        List<MaisVendidoItemDto> itens = new ArrayList<>();
        for (Linha l : atuais) {
            totalUn += l.unidades;
            totalValor = totalValor.add(l.valor);
            totalCusto = totalCusto.add(l.custo);
            catUn.computeIfAbsent(l.categoria, k -> new long[1])[0] += l.unidades;
            catValor.merge(l.categoria, l.valor, BigDecimal::add);
            Linha ant = anteriores.get(l.chave);
            itens.add(new MaisVendidoItemDto(
                    l.chave,
                    l.produtoId,
                    l.comboId,
                    l.vendaUnidade,
                    l.nome,
                    l.categoria,
                    l.unidades,
                    l.valor,
                    l.valor.subtract(l.custo),
                    l.pedidos,
                    ant != null ? ant.unidades : 0L,
                    ant != null ? ant.valor : BigDecimal.ZERO));
        }
        itens.sort(Comparator.comparingLong(MaisVendidoItemDto::unidades)
                .reversed()
                .thenComparing(MaisVendidoItemDto::valor, Comparator.reverseOrder())
                .thenComparing(MaisVendidoItemDto::nome));

        long totalUnAnt = 0;
        BigDecimal totalValorAnt = BigDecimal.ZERO;
        for (Linha l : anteriores.values()) {
            totalUnAnt += l.unidades;
            totalValorAnt = totalValorAnt.add(l.valor);
        }

        List<MaisVendidosCategoriaDto> categorias = new ArrayList<>();
        catUn.forEach((cat, un) -> categorias.add(new MaisVendidosCategoriaDto(cat, un[0], catValor.get(cat))));
        categorias.sort(Comparator.comparing(MaisVendidosCategoriaDto::valor).reversed());

        long pedidos = pedidoRepository.countStatusNoPeriodo(StatusPedido.ENTREGUE, ini, end);
        List<MaisVendidosDiaDto> porDia =
                preencherDias(inicio, fim, pedidoRepository.itensEntreguesPorDia(Timestamp.from(ini), Timestamp.from(end)));

        return new MaisVendidosResponse(
                inicio.toString(),
                fim.toString(),
                antIni.toString(),
                antFim.toString(),
                pedidos,
                totalUn,
                totalValor,
                totalValor.subtract(totalCusto),
                totalUnAnt,
                totalValorAnt,
                itens,
                categorias,
                porDia);
    }

    @Transactional(readOnly = true)
    public MaisVendidosSerieResponse serie(LocalDate inicio, LocalDate fim, String chave) {
        validarIntervalo(inicio, fim);
        if (chave == null || chave.isBlank()) {
            throw new IllegalArgumentException("Informe o item.");
        }
        ZoneId z = CaixaSessaoService.ZONA_OPERACAO;
        Timestamp ini = Timestamp.from(inicio.atStartOfDay(z).toInstant());
        Timestamp end = Timestamp.from(fim.plusDays(1).atStartOfDay(z).toInstant());
        List<Object[]> rows;
        try {
            if (chave.startsWith("d:")) {
                rows = pedidoRepository.avulsoEntreguePorDia(ini, end, chave.substring(2));
            } else if (chave.startsWith("c")) {
                rows = pedidoRepository.comboEntreguePorDia(ini, end, Long.parseLong(chave.substring(1)));
            } else if (chave.startsWith("p") && (chave.endsWith("u") || chave.endsWith("e"))) {
                long id = Long.parseLong(chave.substring(1, chave.length() - 1));
                rows = pedidoRepository.produtoEntreguePorDia(ini, end, id, chave.endsWith("u"));
            } else {
                throw new IllegalArgumentException("Item inválido.");
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Item inválido.");
        }
        return new MaisVendidosSerieResponse(chave, preencherDias(inicio, fim, rows));
    }

    private static void validarIntervalo(LocalDate inicio, LocalDate fim) {
        if (inicio == null || fim == null) {
            throw new IllegalArgumentException("Informe a data inicial e a data final.");
        }
        if (fim.isBefore(inicio)) {
            throw new IllegalArgumentException("A data final não pode ser antes da inicial.");
        }
        if (ChronoUnit.DAYS.between(inicio, fim) + 1 > MAX_DIAS) {
            throw new IllegalArgumentException("Escolha um período de até 2 anos.");
        }
    }

    private List<Linha> carregarLinhas(Instant ini, Instant fim) {
        List<Linha> out = new ArrayList<>();
        for (Object[] r : pedidoRepository.rankingItensEntregues(Timestamp.from(ini), Timestamp.from(fim))) {
            Long produtoId = r[0] instanceof Number n ? n.longValue() : null;
            Long comboId = r[1] instanceof Number n ? n.longValue() : null;
            boolean vendaUnidade = Boolean.TRUE.equals(r[2]);
            String avulso = r[3] != null ? r[3].toString() : null;
            String chave;
            if (produtoId != null) {
                chave = "p" + produtoId + (vendaUnidade ? "u" : "e");
            } else if (comboId != null) {
                chave = "c" + comboId;
            } else {
                chave = "d:" + (avulso != null ? avulso : "Item");
            }
            out.add(new Linha(
                    chave,
                    produtoId,
                    comboId,
                    vendaUnidade,
                    r[4] != null ? r[4].toString() : "Item",
                    r[5] != null ? r[5].toString() : "OUTROS",
                    r[6] instanceof Number n ? n.longValue() : 0L,
                    r[7] instanceof BigDecimal bd ? bd : BigDecimal.ZERO,
                    r[8] instanceof BigDecimal bd ? bd : BigDecimal.ZERO,
                    r[9] instanceof Number n ? n.longValue() : 0L));
        }
        return out;
    }

    private static List<MaisVendidosDiaDto> preencherDias(LocalDate inicio, LocalDate fim, List<Object[]> rows) {
        Map<LocalDate, Object[]> porDia = new HashMap<>();
        for (Object[] r : rows) {
            porDia.put(toLocalDate(r[0]), r);
        }
        List<MaisVendidosDiaDto> out = new ArrayList<>();
        for (LocalDate d = inicio; !d.isAfter(fim); d = d.plusDays(1)) {
            Object[] r = porDia.get(d);
            long un = r != null && r[1] instanceof Number n ? n.longValue() : 0L;
            BigDecimal valor = r != null && r[2] instanceof BigDecimal bd ? bd : BigDecimal.ZERO;
            out.add(new MaisVendidosDiaDto(d.toString(), un, valor));
        }
        return out;
    }

    private static LocalDate toLocalDate(Object o) {
        if (o instanceof LocalDate ld) {
            return ld;
        }
        if (o instanceof java.sql.Date sd) {
            return sd.toLocalDate();
        }
        return LocalDate.parse(o.toString().substring(0, 10));
    }

    private record Linha(
            String chave,
            Long produtoId,
            Long comboId,
            boolean vendaUnidade,
            String nome,
            String categoria,
            long unidades,
            BigDecimal valor,
            BigDecimal custo,
            long pedidos) {}
}
