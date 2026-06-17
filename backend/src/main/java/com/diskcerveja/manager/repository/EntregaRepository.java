package com.diskcerveja.manager.repository;

import com.diskcerveja.manager.domain.entity.Entrega;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EntregaRepository extends JpaRepository<Entrega, Long> {

    Optional<Entrega> findByPedido_Id(Long pedidoId);
}
