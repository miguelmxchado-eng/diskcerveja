package com.diskcerveja.manager.domain.entity;

import com.diskcerveja.manager.domain.enums.StatusEntrega;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "entrega")
public class Entrega {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pedido_id", unique = true, nullable = false)
    private Pedido pedido;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entregador_id")
    private Usuario entregador;

    @Column(name = "entregador_nome", length = 120)
    private String entregadorNome;

    @Column(name = "taxa_entrega", nullable = false, precision = 12, scale = 2)
    private BigDecimal taxaEntrega = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusEntrega status = StatusEntrega.PENDENTE;

    @Column(name = "horario_saida")
    private Instant horarioSaida;

    @Column(name = "horario_entrega")
    private Instant horarioEntrega;

    public Long getId() {
        return id;
    }

    public Pedido getPedido() {
        return pedido;
    }

    public void setPedido(Pedido pedido) {
        this.pedido = pedido;
    }

    public Usuario getEntregador() {
        return entregador;
    }

    public void setEntregador(Usuario entregador) {
        this.entregador = entregador;
    }

    public String getEntregadorNome() {
        return entregadorNome;
    }

    public void setEntregadorNome(String entregadorNome) {
        this.entregadorNome = entregadorNome;
    }

    public BigDecimal getTaxaEntrega() {
        return taxaEntrega;
    }

    public void setTaxaEntrega(BigDecimal taxaEntrega) {
        this.taxaEntrega = taxaEntrega;
    }

    public StatusEntrega getStatus() {
        return status;
    }

    public void setStatus(StatusEntrega status) {
        this.status = status;
    }

    public Instant getHorarioSaida() {
        return horarioSaida;
    }

    public void setHorarioSaida(Instant horarioSaida) {
        this.horarioSaida = horarioSaida;
    }

    public Instant getHorarioEntrega() {
        return horarioEntrega;
    }

    public void setHorarioEntrega(Instant horarioEntrega) {
        this.horarioEntrega = horarioEntrega;
    }
}
