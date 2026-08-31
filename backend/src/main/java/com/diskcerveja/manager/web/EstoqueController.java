package com.diskcerveja.manager.web;

import com.diskcerveja.manager.domain.entity.MovimentoEstoque;
import com.diskcerveja.manager.domain.entity.Produto;
import com.diskcerveja.manager.dto.AjusteEstoqueRequest;
import com.diskcerveja.manager.dto.EntradaEstoqueRequest;
import com.diskcerveja.manager.dto.ProdutoDto;
import com.diskcerveja.manager.security.SecurityUtils;
import com.diskcerveja.manager.service.EstoqueService;
import com.diskcerveja.manager.service.ProdutoService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/estoque")
public class EstoqueController {

    private final EstoqueService estoqueService;
    private final ProdutoService produtoService;

    public EstoqueController(EstoqueService estoqueService, ProdutoService produtoService) {
        this.estoqueService = estoqueService;
        this.produtoService = produtoService;
    }

    @GetMapping("/movimentos")
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR')")
    public List<MovimentoEstoque> movimentos() {
        return estoqueService.historicoRecente();
    }

    @GetMapping("/baixo")
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR')")
    public List<ProdutoDto> baixo() {
        return produtoService.listarBaixoEstoque().stream().map(EstoqueController::toDto).toList();
    }

    @PostMapping("/produto/{id}/ajuste")
    @PreAuthorize("hasRole('ADMIN')")
    public ProdutoDto ajuste(@PathVariable Long id, @RequestBody @Valid AjusteEstoqueRequest req) {
        Produto p = estoqueService.ajustar(id, req.novaQuantidade(), req.motivo(), SecurityUtils.currentUser());
        return toDto(p);
    }

    @PostMapping("/produto/{id}/entrada")
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR')")
    public ProdutoDto entrada(@PathVariable Long id, @RequestBody @Valid EntradaEstoqueRequest req) {
        estoqueService.registrarEntradaCompra(id, req.quantidade(), req.motivo(), SecurityUtils.currentUser());
        return toDto(produtoService.buscarPorId(id));
    }

    private static ProdutoDto toDto(Produto p) {
        return new ProdutoDto(
                p.getId(),
                p.getNome(),
                p.getCodigoBarras(),
                p.getCodigoQr(),
                p.getCodigoInterno(),
                p.getCategoria(),
                p.getPreco(),
                p.getPrecoUnidade(),
                p.getUnidadesPorEmbalagem(),
                p.getCusto(),
                p.getCustoEmbalagem(),
                p.getEstoqueAtual(),
                p.getEstoqueMinimo(),
                p.isAtivo());
    }
}
