package br.com.emporiomachado.emissorfiscal.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface NotaFiscalRepository extends JpaRepository<NotaFiscal, UUID> {

    @Query("select coalesce(max(n.numero), 0) from NotaFiscal n where n.serie = :serie")
    long findMaxNumeroBySerie(int serie);

    Optional<NotaFiscal> findByChaveAcesso(String chaveAcesso);
}
