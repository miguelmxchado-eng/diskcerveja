package com.diskcerveja.manager.web;

import com.diskcerveja.manager.dto.EntregaResumoResponse;
import com.diskcerveja.manager.service.EntregaConsultaService;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/entregas")
public class EntregaController {

    private final EntregaConsultaService entregaConsultaService;

    public EntregaController(EntregaConsultaService entregaConsultaService) {
        this.entregaConsultaService = entregaConsultaService;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<EntregaResumoResponse> listar() {
        return entregaConsultaService.listarPainel();
    }
}
