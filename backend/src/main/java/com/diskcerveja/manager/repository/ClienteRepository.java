package com.diskcerveja.manager.repository;

import com.diskcerveja.manager.domain.entity.Cliente;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClienteRepository extends JpaRepository<Cliente, Long> {

    List<Cliente> findTop200ByAtivoTrueOrderByNomeAsc();

    @Query(
            """
            SELECT c FROM Cliente c
            WHERE c.ativo = true
              AND (
                LOWER(c.nome) LIKE LOWER(CONCAT('%', :q, '%'))
                OR REPLACE(COALESCE(c.telefone, ''), ' ', '') LIKE CONCAT('%', :qDigits, '%')
              )
            ORDER BY c.nome ASC
            """)
    List<Cliente> buscarAtivos(@Param("q") String q, @Param("qDigits") String qDigits);
}
