package com.diskcerveja.manager.web;

import com.diskcerveja.manager.dto.ConfigCaixaPatchRequest;
import com.diskcerveja.manager.dto.ConfigCaixaResponse;
import com.diskcerveja.manager.service.ConfigSistemaService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/config")
public class ConfigController {

    private final ConfigSistemaService configSistemaService;

    public ConfigController(ConfigSistemaService configSistemaService) {
        this.configSistemaService = configSistemaService;
    }

    @GetMapping("/caixa")
    @PreAuthorize("hasRole('ADMIN')")
    public ConfigCaixaResponse getCaixa() {
        return new ConfigCaixaResponse(configSistemaService.isCaixaObrigatorio());
    }

    @PatchMapping("/caixa")
    @PreAuthorize("hasRole('ADMIN')")
    public ConfigCaixaResponse patchCaixa(@RequestBody @Valid ConfigCaixaPatchRequest req) {
        configSistemaService.setCaixaObrigatorio(req.caixaObrigatorio());
        return new ConfigCaixaResponse(configSistemaService.isCaixaObrigatorio());
    }
}
