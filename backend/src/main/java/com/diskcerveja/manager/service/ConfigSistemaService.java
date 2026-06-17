package com.diskcerveja.manager.service;

import com.diskcerveja.manager.domain.entity.ConfigSistema;
import com.diskcerveja.manager.repository.ConfigSistemaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConfigSistemaService {

    private final ConfigSistemaRepository repository;

    public ConfigSistemaService(ConfigSistemaRepository repository) {
        this.repository = repository;
    }

    public boolean isCaixaObrigatorio() {
        return repository
                .findById(ConfigSistema.CHAVE_CAIXA_OBRIGATORIO)
                .map(c -> Boolean.parseBoolean(c.getValor()))
                .orElse(true);
    }

    @Transactional
    public void setCaixaObrigatorio(boolean obrigatorio) {
        ConfigSistema c = repository
                .findById(ConfigSistema.CHAVE_CAIXA_OBRIGATORIO)
                .orElseGet(() -> {
                    ConfigSistema n = new ConfigSistema();
                    n.setChave(ConfigSistema.CHAVE_CAIXA_OBRIGATORIO);
                    return n;
                });
        c.setValor(Boolean.toString(obrigatorio));
        repository.save(c);
    }
}
