package com.diskcerveja.manager.web;

import com.diskcerveja.manager.domain.entity.Produto;
import com.diskcerveja.manager.dto.ProdutoDto;
import com.diskcerveja.manager.dto.ValidacaoCodigoResponse;
import com.diskcerveja.manager.service.ProdutoService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/produtos")
public class ProdutoController {

    private final ProdutoService produtoService;

    public ProdutoController(ProdutoService produtoService) {
        this.produtoService = produtoService;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<ProdutoDto> listar(@RequestParam(required = false) String q) {
        return produtoService.buscar(q).stream().map(ProdutoController::toDto).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ProdutoDto buscar(@PathVariable Long id) {
        return toDto(produtoService.buscarPorId(id));
    }

    @GetMapping("/codigo-barras/{codigo}")
    @PreAuthorize("isAuthenticated()")
    public ProdutoDto buscarPorCodigo(@PathVariable String codigo) {
        return toDto(produtoService.buscarPorCodigoBarras(codigo));
    }

    @GetMapping("/validar-codigo")
    @PreAuthorize("isAuthenticated()")
    public ValidacaoCodigoResponse validarCodigo(
            @RequestParam String codigo, @RequestParam(required = false) Long excluirId) {
        return produtoService.validarCodigo(codigo, excluirId);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ProdutoDto criar(@RequestBody @Valid ProdutoDto dto) {
        return toDto(produtoService.salvar(dto));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ProdutoDto atualizar(@PathVariable Long id, @RequestBody @Valid ProdutoDto dto) {
        return toDto(produtoService.salvar(new ProdutoDto(
                id,
                dto.nome(),
                dto.codigoBarras(),
                dto.codigoQr(),
                dto.codigoInterno(),
                dto.categoria(),
                dto.preco(),
                dto.precoUnidade(),
                dto.unidadesPorEmbalagem(),
                dto.custo(),
                dto.custoEmbalagem(),
                dto.estoqueAtual(),
                dto.estoqueMinimo(),
                dto.ativo())));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public void desativar(@PathVariable Long id) {
        produtoService.desativar(id);
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
