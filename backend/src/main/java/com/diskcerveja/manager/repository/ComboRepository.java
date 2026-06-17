package com.diskcerveja.manager.repository;

import com.diskcerveja.manager.domain.entity.Combo;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ComboRepository extends JpaRepository<Combo, Long> {

    @Query("select distinct c from Combo c left join fetch c.itens i left join fetch i.produto where c.id = :id")
    Optional<Combo> findByIdWithItens(Long id);

    @Query("select distinct c from Combo c left join fetch c.itens i left join fetch i.produto order by c.nome")
    List<Combo> findAllWithItens();

    @Query("select distinct c from Combo c left join fetch c.itens i left join fetch i.produto where c.ativo = true order by c.nome")
    List<Combo> findAtivosWithItens();

    @Query("""
            select distinct c from Combo c
            left join fetch c.itens i
            left join fetch i.produto
            where c.ativo = true
              and (
                c.codigoBarras = :codigo
                or c.codigoQr = :codigo
                or c.codigo = :codigo
              )
            """)
    Optional<Combo> findAtivoByQualquerCodigo(String codigo);

    @Query("""
            select c from Combo c
            where (
                c.codigoBarras = :codigo
                or c.codigoQr = :codigo
                or c.codigo = :codigo
              )
              and (:excluirId is null or c.id <> :excluirId)
            """)
    Optional<Combo> findConflitoCodigo(String codigo, Long excluirId);

    boolean existsByCodigo(String codigo);
}
