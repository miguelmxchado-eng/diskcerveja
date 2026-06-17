package com.diskcerveja.manager.repository;

import com.diskcerveja.manager.domain.entity.Produto;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ProdutoRepository extends JpaRepository<Produto, Long> {

    List<Produto> findByAtivoTrueOrderByNomeAsc();

    @Query("""
            select p from Produto p
            where p.ativo = true
              and (
                lower(p.nome) like lower(concat('%', :q, '%'))
                or p.codigoBarras = :q
                or p.codigoQr = :q
                or p.codigoInterno = :q
                or (p.codigoBarras is not null and p.codigoBarras like concat('%', :q, '%'))
                or (p.codigoInterno is not null and p.codigoInterno like concat('%', :q, '%'))
              )
            order by p.nome
            """)
    List<Produto> searchAtivos(String q);

    Optional<Produto> findByCodigoBarrasAndAtivoTrue(String codigoBarras);

    @Query("""
            select p from Produto p
            where p.ativo = true
              and (
                p.codigoBarras = :codigo
                or p.codigoQr = :codigo
                or p.codigoInterno = :codigo
              )
            """)
    Optional<Produto> findAtivoByQualquerCodigo(String codigo);

    @Query("""
            select p from Produto p
            where (
                p.codigoBarras = :codigo
                or p.codigoQr = :codigo
                or p.codigoInterno = :codigo
              )
              and (:excluirId is null or p.id <> :excluirId)
            """)
    Optional<Produto> findConflitoCodigo(String codigo, Long excluirId);

    @Query("select p from Produto p where p.ativo = true and p.estoqueAtual <= p.estoqueMinimo order by p.nome")
    List<Produto> findBaixoEstoque();
}
