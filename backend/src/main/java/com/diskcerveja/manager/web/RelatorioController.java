package com.diskcerveja.manager.web;

import com.diskcerveja.manager.dto.MaisVendidosResponse;
import com.diskcerveja.manager.dto.MaisVendidosSerieResponse;
import com.diskcerveja.manager.service.MaisVendidosService;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/relatorios")
public class RelatorioController {

    private final MaisVendidosService maisVendidosService;

    public RelatorioController(MaisVendidosService maisVendidosService) {
        this.maisVendidosService = maisVendidosService;
    }

    @GetMapping("/mais-vendidos")
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR')")
    public MaisVendidosResponse maisVendidos(
            @RequestParam("inicio") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam("fim") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {
        return maisVendidosService.ranking(inicio, fim);
    }

    @GetMapping("/mais-vendidos/serie")
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR')")
    public MaisVendidosSerieResponse serie(
            @RequestParam("inicio") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam("fim") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            @RequestParam("chave") String chave) {
        return maisVendidosService.serie(inicio, fim, chave);
    }
}
