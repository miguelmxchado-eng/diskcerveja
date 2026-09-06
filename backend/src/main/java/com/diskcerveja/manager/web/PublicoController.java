package com.diskcerveja.manager.web;

import com.diskcerveja.manager.dto.CatalogoPublicoResponse;
import com.diskcerveja.manager.dto.CatalogoPublicoResponse.LojaPublicaDto;
import com.diskcerveja.manager.dto.InfinitePayWebhookRequest;
import com.diskcerveja.manager.dto.PedidoPublicoRequest;
import com.diskcerveja.manager.dto.PedidoPublicoResponse;
import com.diskcerveja.manager.service.CatalogoPublicoService;
import com.diskcerveja.manager.service.ConfigSistemaService;
import com.diskcerveja.manager.service.PedidoPublicoService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/publico")
public class PublicoController {

    private final CatalogoPublicoService catalogoPublicoService;
    private final PedidoPublicoService pedidoPublicoService;
    private final ConfigSistemaService configSistemaService;

    public PublicoController(
            CatalogoPublicoService catalogoPublicoService,
            PedidoPublicoService pedidoPublicoService,
            ConfigSistemaService configSistemaService) {
        this.catalogoPublicoService = catalogoPublicoService;
        this.pedidoPublicoService = pedidoPublicoService;
        this.configSistemaService = configSistemaService;
    }

    @GetMapping("/loja")
    public LojaPublicaDto loja() {
        return catalogoPublicoService.loja();
    }

    @GetMapping("/catalogo")
    public CatalogoPublicoResponse catalogo(@RequestParam(required = false) String q) {
        return catalogoPublicoService.catalogo(q);
    }

    @PostMapping("/pedidos")
    @ResponseStatus(HttpStatus.CREATED)
    public PedidoPublicoResponse criarPedido(
            @RequestBody @Valid PedidoPublicoRequest req, HttpServletRequest http) {
        return pedidoPublicoService.criar(req, resolverBaseUrl(http));
    }

    @PostMapping("/infinitepay/webhook")
    public ResponseEntity<Map<String, String>> infinitepayWebhook(
            @RequestBody InfinitePayWebhookRequest body) {
        pedidoPublicoService.confirmarPagamentoWebhook(body);
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    private String resolverBaseUrl(HttpServletRequest http) {
        String configured = configSistemaService.getPublicBaseUrl();
        if (!configured.isBlank()) {
            return configured;
        }
        String proto = headerOr(http, "X-Forwarded-Proto", http.getScheme());
        String host = headerOr(http, "X-Forwarded-Host", null);
        if (host == null || host.isBlank()) {
            host = http.getServerName();
            int port = http.getServerPort();
            if (port != 80 && port != 443) {
                host = host + ":" + port;
            }
        }
        return proto + "://" + host;
    }

    private static String headerOr(HttpServletRequest http, String name, String fallback) {
        String v = http.getHeader(name);
        return v != null && !v.isBlank() ? v : fallback;
    }
}
