package com.diskcerveja.manager.web;

import com.diskcerveja.manager.dto.CatalogoPublicoResponse;
import com.diskcerveja.manager.dto.CatalogoPublicoResponse.LojaPublicaDto;
import com.diskcerveja.manager.dto.PedidoPublicoRequest;
import com.diskcerveja.manager.dto.PedidoPublicoResponse;
import com.diskcerveja.manager.service.CatalogoPublicoService;
import com.diskcerveja.manager.service.PedidoPublicoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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

    public PublicoController(
            CatalogoPublicoService catalogoPublicoService, PedidoPublicoService pedidoPublicoService) {
        this.catalogoPublicoService = catalogoPublicoService;
        this.pedidoPublicoService = pedidoPublicoService;
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
    public PedidoPublicoResponse criarPedido(@RequestBody @Valid PedidoPublicoRequest req) {
        return pedidoPublicoService.criar(req);
    }
}
