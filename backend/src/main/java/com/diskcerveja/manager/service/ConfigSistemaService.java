package com.diskcerveja.manager.service;

import com.diskcerveja.manager.domain.entity.ConfigSistema;
import com.diskcerveja.manager.dto.LojaConfigPatchRequest;
import com.diskcerveja.manager.dto.LojaConfigResponse;
import com.diskcerveja.manager.repository.ConfigSistemaRepository;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConfigSistemaService {

    private final ConfigSistemaRepository repository;

    public ConfigSistemaService(ConfigSistemaRepository repository) {
        this.repository = repository;
    }

    public boolean isCaixaObrigatorio() {
        return Boolean.parseBoolean(get(ConfigSistema.CHAVE_CAIXA_OBRIGATORIO, "true"));
    }

    @Transactional
    public void setCaixaObrigatorio(boolean obrigatorio) {
        put(ConfigSistema.CHAVE_CAIXA_OBRIGATORIO, Boolean.toString(obrigatorio));
    }

    public LojaConfigResponse getLoja() {
        return new LojaConfigResponse(
                get(ConfigSistema.CHAVE_LOJA_NOME, "Empório Machado"),
                get(ConfigSistema.CHAVE_LOJA_WHATSAPP, ""),
                Boolean.parseBoolean(get(ConfigSistema.CHAVE_LOJA_ABERTA, "true")),
                get(ConfigSistema.CHAVE_LOJA_HORARIO, "Seg–Dom · 18h às 02h"),
                parseDecimal(get(ConfigSistema.CHAVE_LOJA_TAXA_ENTREGA, "5.00")),
                parseDecimal(get(ConfigSistema.CHAVE_LOJA_PEDIDO_MINIMO, "20.00")),
                get(ConfigSistema.CHAVE_LOJA_INFO, ""));
    }

    @Transactional
    public LojaConfigResponse patchLoja(LojaConfigPatchRequest req) {
        if (req.nome() != null) {
            put(ConfigSistema.CHAVE_LOJA_NOME, req.nome().trim());
        }
        if (req.whatsapp() != null) {
            put(ConfigSistema.CHAVE_LOJA_WHATSAPP, req.whatsapp().trim());
        }
        if (req.aberta() != null) {
            put(ConfigSistema.CHAVE_LOJA_ABERTA, Boolean.toString(req.aberta()));
        }
        if (req.horario() != null) {
            put(ConfigSistema.CHAVE_LOJA_HORARIO, req.horario().trim());
        }
        if (req.taxaEntrega() != null) {
            put(ConfigSistema.CHAVE_LOJA_TAXA_ENTREGA, req.taxaEntrega().toPlainString());
        }
        if (req.pedidoMinimo() != null) {
            put(ConfigSistema.CHAVE_LOJA_PEDIDO_MINIMO, req.pedidoMinimo().toPlainString());
        }
        if (req.info() != null) {
            put(ConfigSistema.CHAVE_LOJA_INFO, req.info().trim());
        }
        return getLoja();
    }

    private String get(String chave, String padrao) {
        return repository.findById(chave).map(ConfigSistema::getValor).orElse(padrao);
    }

    private void put(String chave, String valor) {
        ConfigSistema c = repository.findById(chave).orElseGet(() -> {
            ConfigSistema n = new ConfigSistema();
            n.setChave(chave);
            return n;
        });
        c.setValor(valor != null ? valor : "");
        repository.save(c);
    }

    private static BigDecimal parseDecimal(String raw) {
        try {
            return new BigDecimal(raw);
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }
}
