package com.diskcerveja.manager.repository;

import com.diskcerveja.manager.domain.entity.MovimentoEstoque;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MovimentoEstoqueRepository extends JpaRepository<MovimentoEstoque, Long> {

    @Query("select m from MovimentoEstoque m join fetch m.produto order by m.createdAt desc")
    List<MovimentoEstoque> findRecentWithProduto(Pageable pageable);
}
