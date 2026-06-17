package com.diskcerveja.manager.repository;

import com.diskcerveja.manager.domain.entity.MovimentoCaixa;
import com.diskcerveja.manager.domain.enums.TipoMovimentoCaixa;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MovimentoCaixaRepository extends JpaRepository<MovimentoCaixa, Long> {

    List<MovimentoCaixa> findByCaixaSessaoIdOrderByCreatedAtDesc(Long caixaSessaoId);

    @Query(
            "select coalesce(sum(m.valor),0) from MovimentoCaixa m where m.caixaSessao.id = :sessaoId and m.tipo = :tipo")
    BigDecimal sumValorByTipo(@Param("sessaoId") Long sessaoId, @Param("tipo") TipoMovimentoCaixa tipo);

    boolean existsByPedido_IdAndTipo(Long pedidoId, TipoMovimentoCaixa tipo);

    Optional<MovimentoCaixa> findByPedido_IdAndTipo(Long pedidoId, TipoMovimentoCaixa tipo);

    @Query(
            "select distinct m.pedido.id from MovimentoCaixa m where m.tipo = :tipo and m.pedido is not null and m.pedido.id in :ids")
    List<Long> findPedidoIdsComEntradaVenda(@Param("tipo") TipoMovimentoCaixa tipo, @Param("ids") Collection<Long> ids);
}
