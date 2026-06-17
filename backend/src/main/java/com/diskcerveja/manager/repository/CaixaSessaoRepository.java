package com.diskcerveja.manager.repository;

import com.diskcerveja.manager.domain.entity.CaixaSessao;
import com.diskcerveja.manager.domain.enums.StatusCaixaSessao;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CaixaSessaoRepository extends JpaRepository<CaixaSessao, Long> {

    Optional<CaixaSessao> findByDataReferenciaAndStatus(LocalDate data, StatusCaixaSessao status);

    Optional<CaixaSessao> findFirstByDataReferenciaOrderByIdDesc(LocalDate data);
}
