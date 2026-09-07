package com.diskcerveja.manager.repository;

import com.diskcerveja.manager.domain.entity.ZonaEntrega;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ZonaEntregaRepository extends JpaRepository<ZonaEntrega, Long> {

    List<ZonaEntrega> findByAtivoTrueOrderByOrdemAscIdAsc();

    List<ZonaEntrega> findAllByOrderByOrdemAscIdAsc();
}
