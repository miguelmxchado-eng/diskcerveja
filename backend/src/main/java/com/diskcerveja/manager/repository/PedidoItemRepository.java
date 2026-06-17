package com.diskcerveja.manager.repository;

import com.diskcerveja.manager.domain.entity.PedidoItem;
import com.diskcerveja.manager.dto.ComboVendaAgg;
import com.diskcerveja.manager.domain.enums.StatusPedido;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PedidoItemRepository extends JpaRepository<PedidoItem, Long> {

    /** Agrega vendas de combos em pedidos entregues (quantidade e faturamento). */
    @Query("""
            select new com.diskcerveja.manager.dto.ComboVendaAgg(
                pi.combo.id,
                coalesce(sum(pi.quantidade), 0),
                coalesce(sum(pi.precoUnitario * pi.quantidade), 0)
            )
            from PedidoItem pi
            where pi.combo is not null
              and pi.pedido.status = :status
            group by pi.combo.id
            """)
    List<ComboVendaAgg> agregarVendasCombosPorStatus(StatusPedido status);
}
