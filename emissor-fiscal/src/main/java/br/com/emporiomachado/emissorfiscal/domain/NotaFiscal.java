package br.com.emporiomachado.emissorfiscal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notas_fiscais", uniqueConstraints = {
        @UniqueConstraint(name = "uk_serie_numero", columnNames = {"serie", "numero"})
})
public class NotaFiscal {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private Integer serie;

    @Column(nullable = false)
    private Long numero;

    @Column(length = 44)
    private String chaveAcesso;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotaStatus status = NotaStatus.RASCUNHO;

    @Column(length = 10)
    private String cStat;

    @Column(length = 500)
    private String xMotivo;

    @Column(length = 20)
    private String protocolo;

    @Column(precision = 15, scale = 2)
    private BigDecimal valorTotal;

    @Column(length = 80)
    private String pedidoExternoId;

    @Lob
    @Column(columnDefinition = "CLOB")
    private String xmlEnvio;

    @Lob
    @Column(columnDefinition = "CLOB")
    private String xmlAutorizado;

    @Lob
    @Column(columnDefinition = "CLOB")
    private String xmlCancelamento;

    @Column(length = 2000)
    private String qrCode;

    @Column(nullable = false, updatable = false)
    private Instant criadoEm;

    private Instant atualizadoEm;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        criadoEm = now;
        atualizadoEm = now;
    }

    @PreUpdate
    void onUpdate() {
        atualizadoEm = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Integer getSerie() {
        return serie;
    }

    public void setSerie(Integer serie) {
        this.serie = serie;
    }

    public Long getNumero() {
        return numero;
    }

    public void setNumero(Long numero) {
        this.numero = numero;
    }

    public String getChaveAcesso() {
        return chaveAcesso;
    }

    public void setChaveAcesso(String chaveAcesso) {
        this.chaveAcesso = chaveAcesso;
    }

    public NotaStatus getStatus() {
        return status;
    }

    public void setStatus(NotaStatus status) {
        this.status = status;
    }

    public String getCStat() {
        return cStat;
    }

    public void setCStat(String cStat) {
        this.cStat = cStat;
    }

    public String getXMotivo() {
        return xMotivo;
    }

    public void setXMotivo(String xMotivo) {
        this.xMotivo = xMotivo;
    }

    public String getProtocolo() {
        return protocolo;
    }

    public void setProtocolo(String protocolo) {
        this.protocolo = protocolo;
    }

    public BigDecimal getValorTotal() {
        return valorTotal;
    }

    public void setValorTotal(BigDecimal valorTotal) {
        this.valorTotal = valorTotal;
    }

    public String getPedidoExternoId() {
        return pedidoExternoId;
    }

    public void setPedidoExternoId(String pedidoExternoId) {
        this.pedidoExternoId = pedidoExternoId;
    }

    public String getXmlEnvio() {
        return xmlEnvio;
    }

    public void setXmlEnvio(String xmlEnvio) {
        this.xmlEnvio = xmlEnvio;
    }

    public String getXmlAutorizado() {
        return xmlAutorizado;
    }

    public void setXmlAutorizado(String xmlAutorizado) {
        this.xmlAutorizado = xmlAutorizado;
    }

    public String getXmlCancelamento() {
        return xmlCancelamento;
    }

    public void setXmlCancelamento(String xmlCancelamento) {
        this.xmlCancelamento = xmlCancelamento;
    }

    public String getQrCode() {
        return qrCode;
    }

    public void setQrCode(String qrCode) {
        this.qrCode = qrCode;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }

    public Instant getAtualizadoEm() {
        return atualizadoEm;
    }
}
