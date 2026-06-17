package com.diskcerveja.manager.domain.entity;

import com.diskcerveja.manager.domain.enums.StatusCaixaSessao;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "caixa_sessao")
public class CaixaSessao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "data_referencia", nullable = false)
    private LocalDate dataReferencia;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusCaixaSessao status = StatusCaixaSessao.ABERTO;

    @Column(name = "hora_abertura", nullable = false)
    private Instant horaAbertura;

    @Column(name = "hora_fechamento")
    private Instant horaFechamento;

    @Column(name = "valor_abertura", nullable = false, precision = 14, scale = 2)
    private BigDecimal valorAbertura = BigDecimal.ZERO;

    @Column(name = "valor_fechamento", precision = 14, scale = 2)
    private BigDecimal valorFechamento;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_abertura_id")
    private Usuario usuarioAbertura;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_fechamento_id")
    private Usuario usuarioFechamento;

    public Long getId() {
        return id;
    }

    public LocalDate getDataReferencia() {
        return dataReferencia;
    }

    public void setDataReferencia(LocalDate dataReferencia) {
        this.dataReferencia = dataReferencia;
    }

    public StatusCaixaSessao getStatus() {
        return status;
    }

    public void setStatus(StatusCaixaSessao status) {
        this.status = status;
    }

    public Instant getHoraAbertura() {
        return horaAbertura;
    }

    public void setHoraAbertura(Instant horaAbertura) {
        this.horaAbertura = horaAbertura;
    }

    public Instant getHoraFechamento() {
        return horaFechamento;
    }

    public void setHoraFechamento(Instant horaFechamento) {
        this.horaFechamento = horaFechamento;
    }

    public BigDecimal getValorAbertura() {
        return valorAbertura;
    }

    public void setValorAbertura(BigDecimal valorAbertura) {
        this.valorAbertura = valorAbertura;
    }

    public BigDecimal getValorFechamento() {
        return valorFechamento;
    }

    public void setValorFechamento(BigDecimal valorFechamento) {
        this.valorFechamento = valorFechamento;
    }

    public Usuario getUsuarioAbertura() {
        return usuarioAbertura;
    }

    public void setUsuarioAbertura(Usuario usuarioAbertura) {
        this.usuarioAbertura = usuarioAbertura;
    }

    public Usuario getUsuarioFechamento() {
        return usuarioFechamento;
    }

    public void setUsuarioFechamento(Usuario usuarioFechamento) {
        this.usuarioFechamento = usuarioFechamento;
    }
}
