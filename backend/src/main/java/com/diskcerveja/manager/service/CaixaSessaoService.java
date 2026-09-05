package com.diskcerveja.manager.service;

import com.diskcerveja.manager.domain.entity.CaixaSessao;
import com.diskcerveja.manager.domain.entity.MovimentoCaixa;
import com.diskcerveja.manager.domain.entity.Pedido;
import com.diskcerveja.manager.domain.entity.Usuario;
import com.diskcerveja.manager.domain.enums.StatusCaixaSessao;
import com.diskcerveja.manager.domain.enums.StatusPedido;
import com.diskcerveja.manager.domain.enums.TipoMovimentoCaixa;
import com.diskcerveja.manager.repository.CaixaSessaoRepository;
import com.diskcerveja.manager.repository.MovimentoCaixaRepository;
import com.diskcerveja.manager.repository.PedidoRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CaixaSessaoService {

    public static final ZoneId ZONA_OPERACAO =
            ZoneId.of(com.diskcerveja.manager.config.TimeZoneConfig.ZONE);

    private final CaixaSessaoRepository caixaSessaoRepository;
    private final MovimentoCaixaRepository movimentoCaixaRepository;
    private final PedidoRepository pedidoRepository;

    public CaixaSessaoService(
            CaixaSessaoRepository caixaSessaoRepository,
            MovimentoCaixaRepository movimentoCaixaRepository,
            PedidoRepository pedidoRepository) {
        this.caixaSessaoRepository = caixaSessaoRepository;
        this.movimentoCaixaRepository = movimentoCaixaRepository;
        this.pedidoRepository = pedidoRepository;
    }

    public LocalDate hoje() {
        return LocalDate.now(ZONA_OPERACAO);
    }

    public CaixaSessao sessaoAbertaHoje() {
        return caixaSessaoRepository
                .findByDataReferenciaAndStatus(hoje(), StatusCaixaSessao.ABERTO)
                .orElse(null);
    }

    /**
     * Sessão do dia para lançar venda: caixa aberto do dia; se já fechou, a última sessão registrada naquela data.
     */
    public CaixaSessao obterSessaoParaData(LocalDate dataReferencia) {
        Optional<CaixaSessao> aberto =
                caixaSessaoRepository.findByDataReferenciaAndStatus(dataReferencia, StatusCaixaSessao.ABERTO);
        if (aberto.isPresent()) {
            return aberto.get();
        }
        return caixaSessaoRepository.findFirstByDataReferenciaOrderByIdDesc(dataReferencia).orElse(null);
    }

    @Transactional
    public CaixaSessao abrir(Usuario usuario, BigDecimal valorAbertura) {
        if (valorAbertura == null || valorAbertura.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Valor de abertura inválido.");
        }
        caixaSessaoRepository
                .findByDataReferenciaAndStatus(hoje(), StatusCaixaSessao.ABERTO)
                .ifPresent(s -> {
                    throw new IllegalStateException("Já existe caixa aberto para hoje.");
                });
        CaixaSessao s = new CaixaSessao();
        s.setDataReferencia(hoje());
        s.setStatus(StatusCaixaSessao.ABERTO);
        s.setHoraAbertura(Instant.now());
        s.setValorAbertura(valorAbertura);
        s.setUsuarioAbertura(usuario);
        return caixaSessaoRepository.save(s);
    }

    @Transactional
    public CaixaSessao fechar(Usuario usuario, BigDecimal valorFechamento) {
        CaixaSessao s = caixaSessaoRepository
                .findByDataReferenciaAndStatus(hoje(), StatusCaixaSessao.ABERTO)
                .orElseThrow(() -> new IllegalStateException("Não há caixa aberto para fechar."));
        s.setStatus(StatusCaixaSessao.FECHADO);
        s.setHoraFechamento(Instant.now());
        s.setValorFechamento(valorFechamento);
        s.setUsuarioFechamento(usuario);
        return caixaSessaoRepository.save(s);
    }

    @Transactional
    public MovimentoCaixa registrarSaida(TipoMovimentoCaixa tipo, BigDecimal valor, String descricao) {
        if (tipo == TipoMovimentoCaixa.ENTRADA_VENDA) {
            throw new IllegalArgumentException("Use o fluxo de pedido para entrada de venda.");
        }
        CaixaSessao s = caixaSessaoRepository
                .findByDataReferenciaAndStatus(hoje(), StatusCaixaSessao.ABERTO)
                .orElseThrow(() -> new IllegalStateException("Caixa não está aberto."));
        MovimentoCaixa m = new MovimentoCaixa();
        m.setCaixaSessao(s);
        m.setTipo(tipo);
        m.setValor(valor);
        m.setDescricao(descricao);
        return movimentoCaixaRepository.save(m);
    }

    /**
     * Registra/atualiza venda no caixa do mesmo dia operacional do pedido ({@code dataHora} em São Paulo), alinhado à
     * sincronização.
     */
    @Transactional
    public void registrarVendaPedido(Pedido pedido) {
        LocalDate diaPedido = LocalDate.ofInstant(pedido.getDataHora(), ZONA_OPERACAO);
        registrarOuAtualizarVendaPedido(pedido, diaPedido);
    }

    /**
     * Cria ou atualiza ENTRADA_VENDA vinculada ao pedido na sessão do dia informado. Retorna false se não houver
     * sessão naquele dia.
     */
    @Transactional
    public boolean registrarOuAtualizarVendaPedido(Pedido pedido, LocalDate dataReferenciaSessao) {
        if (pedido.getStatus() != StatusPedido.ENTREGUE) {
            return false;
        }
        CaixaSessao sessao = obterSessaoParaData(dataReferenciaSessao);
        if (sessao == null) {
            return false;
        }
        Optional<MovimentoCaixa> existente =
                movimentoCaixaRepository.findByPedido_IdAndTipo(pedido.getId(), TipoMovimentoCaixa.ENTRADA_VENDA);
        if (existente.isPresent()) {
            MovimentoCaixa m = existente.get();
            m.setValor(pedido.getTotal());
            m.setDescricao("Pedido #" + pedido.getId());
            m.setCaixaSessao(sessao);
            movimentoCaixaRepository.save(m);
            return true;
        }
        MovimentoCaixa m = new MovimentoCaixa();
        m.setCaixaSessao(sessao);
        m.setTipo(TipoMovimentoCaixa.ENTRADA_VENDA);
        m.setValor(pedido.getTotal());
        m.setDescricao("Pedido #" + pedido.getId());
        m.setPedido(pedido);
        movimentoCaixaRepository.save(m);
        return true;
    }

    /**
     * Remove a ENTRADA_VENDA do pedido no caixa (ex.: cancelamento após ENTREGUE).
     */
    @Transactional
    public void estornarVendaPedido(Pedido pedido) {
        movimentoCaixaRepository
                .findByPedido_IdAndTipo(pedido.getId(), TipoMovimentoCaixa.ENTRADA_VENDA)
                .ifPresent(movimentoCaixaRepository::delete);
    }

    /**
     * Para pedidos ENTREGUE no intervalo, garante movimento de caixa (dia = data local do pedido) e alinha valor.
     */
    @Transactional
    public int sincronizarVendasPedidos(LocalDate inicio, LocalDate fim) {
        if (inicio.isAfter(fim)) {
            throw new IllegalArgumentException("Data inicial não pode ser depois da final.");
        }
        Instant ini = inicio.atStartOfDay(ZONA_OPERACAO).toInstant();
        Instant end = fim.plusDays(1).atStartOfDay(ZONA_OPERACAO).toInstant();
        List<Pedido> entregues = pedidoRepository.findEntreguesNoPeriodo(ini, end);
        int ok = 0;
        for (Pedido p : entregues) {
            if (registrarOuAtualizarVendaPedido(
                    p, LocalDate.ofInstant(p.getDataHora(), ZONA_OPERACAO))) {
                ok++;
            }
        }
        return ok;
    }

    public List<MovimentoCaixa> movimentosDoCaixaAberto() {
        CaixaSessao s = sessaoAbertaHoje();
        if (s == null) {
            return List.of();
        }
        return movimentoCaixaRepository.findByCaixaSessaoIdOrderByCreatedAtDesc(s.getId());
    }

    public BigDecimal saldoPrevisto(CaixaSessao sessao) {
        BigDecimal entradas = movimentoCaixaRepository.sumValorByTipo(sessao.getId(), TipoMovimentoCaixa.ENTRADA_VENDA);
        BigDecimal saidasTroco = movimentoCaixaRepository.sumValorByTipo(sessao.getId(), TipoMovimentoCaixa.SAIDA_TROCO);
        BigDecimal saidasDesp = movimentoCaixaRepository.sumValorByTipo(sessao.getId(), TipoMovimentoCaixa.SAIDA_DESPESA);
        return sessao.getValorAbertura().add(entradas).subtract(saidasTroco).subtract(saidasDesp);
    }
}
