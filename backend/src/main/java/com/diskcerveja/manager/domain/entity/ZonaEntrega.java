package com.diskcerveja.manager.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "zona_entrega")
public class ZonaEntrega {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String nome;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal taxa = BigDecimal.ZERO;

    /** Prefixos/faixas separados por vírgula. Ex: 75020-75029, 75040 */
    @Column(name = "cep_prefixos", length = 500)
    private String cepPrefixos = "";

    /** Bairros separados por vírgula (mapa Anápolis). */
    @Column(name = "bairros", columnDefinition = "text")
    private String bairros = "";

    @Column(nullable = false)
    private boolean ativo = true;

    @Column(nullable = false)
    private int ordem = 0;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @PrePersist
    void prePersist() {
        if (criadoEm == null) {
            criadoEm = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public BigDecimal getTaxa() {
        return taxa;
    }

    public void setTaxa(BigDecimal taxa) {
        this.taxa = taxa;
    }

    public String getCepPrefixos() {
        return cepPrefixos;
    }

    public void setCepPrefixos(String cepPrefixos) {
        this.cepPrefixos = cepPrefixos;
    }

    public String getBairros() {
        return bairros;
    }

    public void setBairros(String bairros) {
        this.bairros = bairros;
    }

    public boolean isAtivo() {
        return ativo;
    }

    public void setAtivo(boolean ativo) {
        this.ativo = ativo;
    }

    public int getOrdem() {
        return ordem;
    }

    public void setOrdem(int ordem) {
        this.ordem = ordem;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }

    public void setCriadoEm(Instant criadoEm) {
        this.criadoEm = criadoEm;
    }
}
