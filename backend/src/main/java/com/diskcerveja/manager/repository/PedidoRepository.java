package com.diskcerveja.manager.repository;

import com.diskcerveja.manager.domain.entity.Pedido;
import com.diskcerveja.manager.domain.enums.StatusPedido;
import com.diskcerveja.manager.domain.enums.TipoPedido;
import com.diskcerveja.manager.dto.FormaPagamentoAgg;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PedidoRepository extends JpaRepository<Pedido, Long> {

    @Query(
            "select distinct p from Pedido p left join fetch p.itens i left join fetch i.produto left join fetch p.entrega where p.id = :id")
    Optional<Pedido> findByIdWithItens(@Param("id") Long id);

    List<Pedido> findTop200ByOrderByDataHoraDesc();

    List<Pedido> findByStatusInAndDataHoraBefore(List<StatusPedido> statuses, Instant before);

    @Query(
            "select coalesce(sum(p.total),0) from Pedido p where p.status = 'ENTREGUE' and p.dataHora >= :inicio and p.dataHora < :fim")
    java.math.BigDecimal sumTotalEntreguesNoPeriodo(@Param("inicio") Instant inicio, @Param("fim") Instant fim);

    @Query(
            "select new com.diskcerveja.manager.dto.FormaPagamentoAgg(p.formaPagamento, coalesce(sum(p.total),0)) from Pedido p where p.status = 'ENTREGUE' and p.dataHora >= :inicio and p.dataHora < :fim group by p.formaPagamento")
    List<FormaPagamentoAgg> sumByFormaPagamento(@Param("inicio") Instant inicio, @Param("fim") Instant fim);

    long countByStatusIn(List<StatusPedido> statuses);

    @Query(
            "select count(p) from Pedido p where p.status in :statuses and p.dataHora < :limite")
    long countAtrasados(@Param("statuses") List<StatusPedido> statuses, @Param("limite") Instant limite);

    @Query(
            "select p from Pedido p where p.dataHora >= :ini and p.dataHora < :fim order by p.dataHora desc")
    List<Pedido> findByDataHoraBetween(@Param("ini") Instant ini, @Param("fim") Instant fim);

    @Query(
            "select distinct p from Pedido p left join fetch p.itens i left join fetch i.produto left join fetch i.combo where p.dataHora >= :ini and p.dataHora < :fim")
    List<Pedido> findByDataHoraBetweenWithItens(@Param("ini") Instant ini, @Param("fim") Instant fim);

    /**
     * Painel de entregas: pedidos do tipo ENTREGA ainda em rota (status do pedido), com ou sem linha na tabela
     * {@code entrega}.
     */
    @Query(
            "select distinct p from Pedido p left join fetch p.entrega where p.tipo = :tipo and p.status in (:s1, :s2, :s3) order by p.dataHora desc")
    List<Pedido> findEntregaPainel(
            @Param("tipo") TipoPedido tipo,
            @Param("s1") StatusPedido s1,
            @Param("s2") StatusPedido s2,
            @Param("s3") StatusPedido s3);

    @Query(
            "select p from Pedido p where p.status = 'ENTREGUE' and p.dataHora >= :ini and p.dataHora < :fim order by p.dataHora desc")
    List<Pedido> findEntreguesNoPeriodo(@Param("ini") Instant ini, @Param("fim") Instant fim);

    @Query(
            "select count(p) from Pedido p where p.status = :st and p.dataHora >= :i0 and p.dataHora < :i1")
    long countStatusNoPeriodo(
            @Param("st") StatusPedido st, @Param("i0") Instant i0, @Param("i1") Instant i1);

    @Query(
        value = """
            SELECT 
                CAST(p.data_hora AS DATE) AS dia, 
                COALESCE(SUM(CASE WHEN p.status = 'ENTREGUE' THEN p.total ELSE 0 END), 0) AS vendas, 
                COUNT(CASE WHEN p.status = 'CANCELADO' THEN 1 END) AS cancelamentos
            FROM pedido p 
            WHERE p.data_hora >= :ini AND p.data_hora < :fim 
            GROUP BY CAST(p.data_hora AS DATE) 
            ORDER BY dia
        """, 
        nativeQuery = true)
    List<Object[]> aggregateVendasCancelamentosPorDiaOperacao(
            @Param("ini") java.sql.Timestamp ini, @Param("fim") java.sql.Timestamp fim);

    @Query(
            value =
                    """
            SELECT CAST(date_trunc('month', (p.data_hora AT TIME ZONE 'UTC' AT TIME ZONE 'America/Sao_Paulo')) AS date) AS mes,
                   COALESCE(SUM(CASE WHEN p.status = 'ENTREGUE' THEN p.total END), 0) AS vendas,
                   CAST(COALESCE(COUNT(*) FILTER (WHERE p.status = 'CANCELADO'), 0) AS bigint) AS cancelamentos
            FROM pedido p
            WHERE p.data_hora >= :ini AND p.data_hora < :fim
            GROUP BY 1
            ORDER BY 1
            """,
            nativeQuery = true)
    List<Object[]> aggregateVendasCancelamentosPorMesOperacao(
            @Param("ini") java.sql.Timestamp ini, @Param("fim") java.sql.Timestamp fim);
}
