package com.diskcerveja.manager.domain.entity;

import com.diskcerveja.manager.domain.enums.CategoriaProduto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "produto")
public class Produto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nome;

    @Column(name = "codigo_barras", length = 80)
    private String codigoBarras;

    @Column(name = "codigo_qr", length = 80)
    private String codigoQr;

    @Column(name = "codigo_interno", length = 80)
    private String codigoInterno;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CategoriaProduto categoria;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal preco;

    /** Preço de venda da unidade avulsa (opcional; ex.: lata solta de um pack). */
    @Column(name = "preco_unidade", precision = 12, scale = 2)
    private BigDecimal precoUnidade;

    /** Quantas unidades vêm na embalagem quando vende pacote e unidade. */
    @Column(name = "unidades_por_embalagem")
    private Integer unidadesPorEmbalagem;

    @Column(nullable = false, precision = 12, scale = 4)
    private BigDecimal custo;

    @Column(name = "estoque_atual", nullable = false)
    private int estoqueAtual;

    @Column(name = "estoque_minimo", nullable = false)
    private int estoqueMinimo;

    @Column(nullable = false)
    private boolean ativo = true;

    public Long getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getCodigoBarras() {
        return codigoBarras;
    }

    public void setCodigoBarras(String codigoBarras) {
        this.codigoBarras = codigoBarras;
    }

    public String getCodigoQr() {
        return codigoQr;
    }

    public void setCodigoQr(String codigoQr) {
        this.codigoQr = codigoQr;
    }

    public String getCodigoInterno() {
        return codigoInterno;
    }

    public void setCodigoInterno(String codigoInterno) {
        this.codigoInterno = codigoInterno;
    }

    public CategoriaProduto getCategoria() {
        return categoria;
    }

    public void setCategoria(CategoriaProduto categoria) {
        this.categoria = categoria;
    }

    public BigDecimal getPreco() {
        return preco;
    }

    public void setPreco(BigDecimal preco) {
        this.preco = preco;
    }

    public BigDecimal getPrecoUnidade() {
        return precoUnidade;
    }

    public void setPrecoUnidade(BigDecimal precoUnidade) {
        this.precoUnidade = precoUnidade;
    }

    public Integer getUnidadesPorEmbalagem() {
        return unidadesPorEmbalagem;
    }

    public void setUnidadesPorEmbalagem(Integer unidadesPorEmbalagem) {
        this.unidadesPorEmbalagem = unidadesPorEmbalagem;
    }

    public boolean permiteVendaUnidade() {
        return precoUnidade != null
                && precoUnidade.compareTo(BigDecimal.ZERO) > 0
                && unidadesPorEmbalagem != null
                && unidadesPorEmbalagem > 1;
    }

    public BigDecimal getCusto() {
        return custo;
    }

    public void setCusto(BigDecimal custo) {
        this.custo = custo;
    }

    public int getEstoqueAtual() {
        return estoqueAtual;
    }

    public void setEstoqueAtual(int estoqueAtual) {
        this.estoqueAtual = estoqueAtual;
    }

    public int getEstoqueMinimo() {
        return estoqueMinimo;
    }

    public void setEstoqueMinimo(int estoqueMinimo) {
        this.estoqueMinimo = estoqueMinimo;
    }

    public boolean isAtivo() {
        return ativo;
    }

    public void setAtivo(boolean ativo) {
        this.ativo = ativo;
    }
}
