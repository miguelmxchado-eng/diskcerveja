package com.diskcerveja.manager.service;

import com.diskcerveja.manager.domain.entity.Produto;
import com.diskcerveja.manager.dto.ProdutoDto;
import com.diskcerveja.manager.dto.ValidacaoCodigoResponse;
import com.diskcerveja.manager.repository.ProdutoRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProdutoService {

    private final ProdutoRepository produtoRepository;

    public ProdutoService(ProdutoRepository produtoRepository) {
        this.produtoRepository = produtoRepository;
    }

    public List<Produto> listarAtivos() {
        return produtoRepository.findByAtivoTrueOrderByNomeAsc();
    }

    public List<Produto> listarBaixoEstoque() {
        return produtoRepository.findBaixoEstoque();
    }

    public List<Produto> buscar(String q) {
        if (q == null || q.isBlank()) {
            return listarAtivos();
        }
        return produtoRepository.searchAtivos(q.trim());
    }

    public Produto buscarPorId(Long id) {
        return produtoRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Produto não encontrado."));
    }

    public Produto buscarPorCodigoBarras(String codigoBarras) {
        if (codigoBarras == null || codigoBarras.isBlank()) {
            throw new IllegalArgumentException("Código obrigatório.");
        }
        return produtoRepository
                .findAtivoByQualquerCodigo(codigoBarras.trim())
                .orElseThrow(() -> new IllegalArgumentException("Produto não encontrado para o código informado."));
    }

    public ValidacaoCodigoResponse validarCodigo(String codigo, Long excluirId) {
        if (codigo == null || codigo.isBlank()) {
            return new ValidacaoCodigoResponse(true, null, null);
        }
        return produtoRepository
                .findConflitoCodigo(codigo.trim(), excluirId)
                .map(p -> new ValidacaoCodigoResponse(false, p.getId(), p.getNome()))
                .orElse(new ValidacaoCodigoResponse(true, null, null));
    }

    @Transactional
    public Produto salvar(ProdutoDto dto) {
        Produto p = dto.id() == null ? new Produto() : buscarPorId(dto.id());
        p.setNome(dto.nome());
        String codigoBarras = normalizeCodigo(dto.codigoBarras());
        String codigoQr = normalizeCodigo(dto.codigoQr());
        validarDuplicidadeCodigos(codigoBarras, codigoQr, dto.id());
        p.setCodigoBarras(codigoBarras);
        p.setCodigoQr(codigoQr);
        if (codigoBarras == null && codigoQr == null) {
            if (p.getCodigoInterno() == null || p.getCodigoInterno().isBlank()) {
                p.setCodigoInterno(gerarCodigoInterno());
            }
        } else if (dto.codigoInterno() != null && !dto.codigoInterno().isBlank()) {
            p.setCodigoInterno(normalizeCodigo(dto.codigoInterno()));
        }
        p.setCategoria(dto.categoria());
        if (dto.preco() == null || dto.preco().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Preço de venda deve ser maior que zero.");
        }
        if (dto.custo() == null || dto.custo().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Preço de compra é obrigatório e não pode ser negativo.");
        }
        p.setPreco(dto.preco());
        p.setCusto(dto.custo());
        aplicarPrecoUnidade(p, dto);
        if (dto.id() == null) {
            p.setEstoqueAtual(dto.estoqueAtual() != null ? dto.estoqueAtual() : 0);
            p.setEstoqueMinimo(dto.estoqueMinimo() != null ? dto.estoqueMinimo() : 0);
        } else {
            if (dto.estoqueAtual() != null) {
                p.setEstoqueAtual(dto.estoqueAtual());
            }
            if (dto.estoqueMinimo() != null) {
                p.setEstoqueMinimo(dto.estoqueMinimo());
            }
        }
        p.setAtivo(dto.ativo());
        return produtoRepository.save(p);
    }

    private static void aplicarPrecoUnidade(Produto p, ProdutoDto dto) {
        BigDecimal precoUnidade = dto.precoUnidade();
        Integer unidades = dto.unidadesPorEmbalagem();
        boolean temPreco = precoUnidade != null && precoUnidade.compareTo(BigDecimal.ZERO) > 0;
        boolean temUnidades = unidades != null && unidades > 1;
        if (temPreco != temUnidades) {
            throw new IllegalArgumentException(
                    "Para vender unidade, informe o preço da unidade e quantas unidades vêm no pacote (ex.: 6).");
        }
        if (!temPreco) {
            p.setPrecoUnidade(null);
            p.setUnidadesPorEmbalagem(null);
            return;
        }
        p.setPrecoUnidade(precoUnidade);
        p.setUnidadesPorEmbalagem(unidades);
    }

    @Transactional
    public void desativar(Long id) {
        Produto p = buscarPorId(id);
        p.setAtivo(false);
        produtoRepository.save(p);
    }

    private void validarDuplicidadeCodigos(String codigoBarras, String codigoQr, Long excluirId) {
        if (codigoBarras != null) {
            var v = validarCodigo(codigoBarras, excluirId);
            if (!v.disponivel()) {
                throw new IllegalStateException(
                        "Código de barras já cadastrado no produto \"" + v.produtoNome() + "\".");
            }
        }
        if (codigoQr != null) {
            var v = validarCodigo(codigoQr, excluirId);
            if (!v.disponivel()) {
                throw new IllegalStateException(
                        "QR Code já cadastrado no produto \"" + v.produtoNome() + "\".");
            }
        }
    }

    private static String gerarCodigoInterno() {
        return "DC-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }

    private static String normalizeCodigo(String codigo) {
        if (codigo == null || codigo.isBlank()) {
            return null;
        }
        return codigo.trim();
    }
}
