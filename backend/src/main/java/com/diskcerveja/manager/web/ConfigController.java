package com.diskcerveja.manager.web;

import com.diskcerveja.manager.dto.ConfigCaixaPatchRequest;
import com.diskcerveja.manager.dto.ConfigCaixaResponse;
import com.diskcerveja.manager.dto.LojaConfigPatchRequest;
import com.diskcerveja.manager.dto.LojaConfigResponse;
import com.diskcerveja.manager.dto.ZonaEntregaDto;
import com.diskcerveja.manager.service.ConfigSistemaService;
import com.diskcerveja.manager.service.ZonaEntregaService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/config")
public class ConfigController {

    private final ConfigSistemaService configSistemaService;
    private final ZonaEntregaService zonaEntregaService;

    public ConfigController(ConfigSistemaService configSistemaService, ZonaEntregaService zonaEntregaService) {
        this.configSistemaService = configSistemaService;
        this.zonaEntregaService = zonaEntregaService;
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

    @GetMapping("/loja")
    @PreAuthorize("hasRole('ADMIN')")
    public LojaConfigResponse getLoja() {
        return configSistemaService.getLoja();
    }

    @PatchMapping("/loja")
    @PreAuthorize("hasRole('ADMIN')")
    public LojaConfigResponse patchLoja(@RequestBody LojaConfigPatchRequest req) {
        return configSistemaService.patchLoja(req);
    }

    @GetMapping("/zonas-entrega")
    @PreAuthorize("hasRole('ADMIN')")
    public List<ZonaEntregaDto> listarZonas() {
        return zonaEntregaService.listar();
    }

    @PutMapping("/zonas-entrega")
    @PreAuthorize("hasRole('ADMIN')")
    public List<ZonaEntregaDto> salvarZonas(@RequestBody List<@Valid ZonaEntregaDto> zonas) {
        return zonaEntregaService.substituirTodas(zonas);
    }
}
