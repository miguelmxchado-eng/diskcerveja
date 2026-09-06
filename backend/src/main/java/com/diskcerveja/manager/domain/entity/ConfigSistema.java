package com.diskcerveja.manager.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "config_sistema")
public class ConfigSistema {

    public static final String CHAVE_CAIXA_OBRIGATORIO = "caixa.obrigatorio";
    public static final String CHAVE_LOJA_NOME = "loja.nome";
    public static final String CHAVE_LOJA_WHATSAPP = "loja.whatsapp";
    public static final String CHAVE_LOJA_ABERTA = "loja.aberta";
    public static final String CHAVE_LOJA_HORARIO = "loja.horario";
    public static final String CHAVE_LOJA_TAXA_ENTREGA = "loja.taxa_entrega";
    public static final String CHAVE_LOJA_PEDIDO_MINIMO = "loja.pedido_minimo";
    public static final String CHAVE_LOJA_INFO = "loja.info";

    @Id
    @Column(name = "chave", length = 80)
    private String chave;

    @Column(nullable = false, length = 500)
    private String valor;

    public String getChave() {
        return chave;
    }

    public void setChave(String chave) {
        this.chave = chave;
    }

    public String getValor() {
        return valor;
    }

    public void setValor(String valor) {
        this.valor = valor;
    }
}
