package com.diskcerveja.manager.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Rotas do Angular ({@code app.routes.ts}): no refresh o browser pede {@code GET /dashboard} (etc.) ao
 * servidor. Sem este forward o Spring procura {@code /static/dashboard} e retorna 404. O forward
 * devolve {@code index.html} e o roteador do front resolve a URL.
 */
@Controller
public class SpaForwardController {

    private static final String FORWARD_INDEX = "forward:/index.html";

    @GetMapping({
        "/login",
        "/dashboard",
        "/pdv",
        "/relatorio-pedidos",
        "/clientes",
        "/produtos",
        "/estoque",
        "/caixa",
        "/entregas",
        "/usuarios",
        "/config"
    })
    public String forwardAngularRoutes() {
        return FORWARD_INDEX;
    }
}
