package com.diskcerveja.manager.repository;

import com.diskcerveja.manager.domain.entity.ContaFinanceira;
import com.diskcerveja.manager.domain.enums.StatusContaFinanceira;
import com.diskcerveja.manager.domain.enums.TipoContaFinanceira;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ContaFinanceiraRepository extends JpaRepository<ContaFinanceira, Long> {

    @Query("""
            select c from ContaFinanceira c
            where c.tipo = :tipo
              and (:status is null or c.status = :status)
              and (
                :q is null or :q = ''
                or lower(c.descricao) like lower(concat('%', :q, '%'))
                or lower(coalesce(c.pessoa, '')) like lower(concat('%', :q, '%'))
              )
            order by c.vencimento asc, c.id desc
            """)
    List<ContaFinanceira> buscar(
            @Param("tipo") TipoContaFinanceira tipo,
            @Param("status") StatusContaFinanceira status,
            @Param("q") String q);
}
