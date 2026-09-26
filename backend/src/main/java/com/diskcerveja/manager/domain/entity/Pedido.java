package com.diskcerveja.manager.domain.entity;

import com.diskcerveja.manager.domain.enums.FormaPagamento;
import com.diskcerveja.manager.domain.enums.StatusPedido;
import com.diskcerveja.manager.domain.enums.TipoPedido;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "pedido")
public class Pedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "data_hora", nullable = false)
    private Instant dataHora;

    @Column(name = "cliente_nome")
    private String clienteNome;

    private String telefone;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoPedido tipo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusPedido status = StatusPedido.ABERTO;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal total = BigDecimal.ZERO;

    /** Desconto em R$ aplicado sobre o subtotal dos itens (antes da taxa de entrega). */
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal desconto = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "forma_pagamento", nullable = false, length = 20)
    private FormaPagamento formaPagamento;

    @Column(name = "endereco_entrega", length = 500)
    private String enderecoEntrega;

    @Column(name = "estoque_baixado", nullable = false)
    private boolean estoqueBaixado;

    @Column(name = "pagamento_confirmado", nullable = false)
    private boolean pagamentoConfirmado;

    @Column(name = "pagamento_ref", length = 80)
    private String pagamentoRef;

    /** CPF/CNPJ opcional para NFC-e (consumidor). */
    @Column(name = "cpf_na_nota", length = 14)
    private String cpfNaNota;

    @Column(name = "nfce_status", length = 20)
    private String nfceStatus;

    @Column(name = "nfce_chave", length = 50)
    private String nfceChave;

    @Column(name = "nfce_protocolo", length = 30)
    private String nfceProtocolo;

    @Column(name = "nfce_qr_code", columnDefinition = "TEXT")
    private String nfceQrCode;

    @Column(name = "nfce_motivo", length = 500)
    private String nfceMotivo;

    @Column(name = "nfce_id_externo", length = 40)
    private String nfceIdExterno;

    @Column(name = "nfce_emitida_em")
    private Instant nfceEmitidaEm;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PedidoItem> itens = new ArrayList<>();

    @OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("id ASC")
    private List<PedidoPagamento> pagamentos = new ArrayList<>();

    @OneToOne(mappedBy = "pedido", fetch = FetchType.LAZY)
    private Entrega entrega;

    @PrePersist
    void prePersist() {
        if (dataHora == null) {
            dataHora = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public Instant getDataHora() {
        return dataHora;
    }

    public void setDataHora(Instant dataHora) {
        this.dataHora = dataHora;
    }

    public String getClienteNome() {
        return clienteNome;
    }

    public void setClienteNome(String clienteNome) {
        this.clienteNome = clienteNome;
    }

    public String getTelefone() {
        return telefone;
    }

    public void setTelefone(String telefone) {
        this.telefone = telefone;
    }

    public Cliente getCliente() {
        return cliente;
    }

    public void setCliente(Cliente cliente) {
        this.cliente = cliente;
    }

    public TipoPedido getTipo() {
        return tipo;
    }

    public void setTipo(TipoPedido tipo) {
        this.tipo = tipo;
    }

    public StatusPedido getStatus() {
        return status;
    }

    public void setStatus(StatusPedido status) {
        this.status = status;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public void setTotal(BigDecimal total) {
        this.total = total;
    }

    public BigDecimal getDesconto() {
        return desconto;
    }

    public void setDesconto(BigDecimal desconto) {
        this.desconto = desconto != null ? desconto : BigDecimal.ZERO;
    }

    public FormaPagamento getFormaPagamento() {
        return formaPagamento;
    }

    public void setFormaPagamento(FormaPagamento formaPagamento) {
        this.formaPagamento = formaPagamento;
    }

    public String getEnderecoEntrega() {
        return enderecoEntrega;
    }

    public void setEnderecoEntrega(String enderecoEntrega) {
        this.enderecoEntrega = enderecoEntrega;
    }

    public boolean isEstoqueBaixado() {
        return estoqueBaixado;
    }

    public void setEstoqueBaixado(boolean estoqueBaixado) {
        this.estoqueBaixado = estoqueBaixado;
    }

    public boolean isPagamentoConfirmado() {
        return pagamentoConfirmado;
    }

    public void setPagamentoConfirmado(boolean pagamentoConfirmado) {
        this.pagamentoConfirmado = pagamentoConfirmado;
    }

    public String getPagamentoRef() {
        return pagamentoRef;
    }

    public void setPagamentoRef(String pagamentoRef) {
        this.pagamentoRef = pagamentoRef;
    }

    public String getCpfNaNota() {
        return cpfNaNota;
    }

    public void setCpfNaNota(String cpfNaNota) {
        this.cpfNaNota = cpfNaNota;
    }

    public String getNfceStatus() {
        return nfceStatus;
    }

    public void setNfceStatus(String nfceStatus) {
        this.nfceStatus = nfceStatus;
    }

    public String getNfceChave() {
        return nfceChave;
    }

    public void setNfceChave(String nfceChave) {
        this.nfceChave = nfceChave;
    }

    public String getNfceProtocolo() {
        return nfceProtocolo;
    }

    public void setNfceProtocolo(String nfceProtocolo) {
        this.nfceProtocolo = nfceProtocolo;
    }

    public String getNfceQrCode() {
        return nfceQrCode;
    }

    public void setNfceQrCode(String nfceQrCode) {
        this.nfceQrCode = nfceQrCode;
    }

    public String getNfceMotivo() {
        return nfceMotivo;
    }

    public void setNfceMotivo(String nfceMotivo) {
        this.nfceMotivo = nfceMotivo;
    }

    public String getNfceIdExterno() {
        return nfceIdExterno;
    }

    public void setNfceIdExterno(String nfceIdExterno) {
        this.nfceIdExterno = nfceIdExterno;
    }

    public Instant getNfceEmitidaEm() {
        return nfceEmitidaEm;
    }

    public void setNfceEmitidaEm(Instant nfceEmitidaEm) {
        this.nfceEmitidaEm = nfceEmitidaEm;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public void setUsuario(Usuario usuario) {
        this.usuario = usuario;
    }

    public List<PedidoItem> getItens() {
        return itens;
    }

    public void setItens(List<PedidoItem> itens) {
        this.itens = itens;
    }

    public List<PedidoPagamento> getPagamentos() {
        return pagamentos;
    }

    public void setPagamentos(List<PedidoPagamento> pagamentos) {
        this.pagamentos = pagamentos;
    }

    public Entrega getEntrega() {
        return entrega;
    }

    public void setEntrega(Entrega entrega) {
        this.entrega = entrega;
    }
}
