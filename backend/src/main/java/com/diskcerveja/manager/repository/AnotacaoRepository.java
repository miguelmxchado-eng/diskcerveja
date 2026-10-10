package com.diskcerveja.manager.repository;

import com.diskcerveja.manager.domain.entity.Anotacao;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AnotacaoRepository extends JpaRepository<Anotacao, Long> {

    @Query("""
            select a from Anotacao a
            where a.concluida = :concluida
              and (
                :q = ''
                or lower(coalesce(a.titulo, '')) like lower(concat('%', :q, '%'))
                or lower(a.texto) like lower(concat('%', :q, '%'))
              )
            order by a.fixada desc, a.updatedAt desc, a.id desc
            """)
    List<Anotacao> buscar(@Param("concluida") boolean concluida, @Param("q") String q);

    long countByConcluida(boolean concluida);
}
