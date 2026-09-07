package com.diskcerveja.manager.web;

import com.diskcerveja.manager.dto.CatalogoPublicoResponse;
import com.diskcerveja.manager.dto.CatalogoPublicoResponse.LojaPublicaDto;
import com.diskcerveja.manager.dto.ConfirmarPagamentoPublicoRequest;
import com.diskcerveja.manager.dto.ContaClienteAtualizarRequest;
import com.diskcerveja.manager.dto.ContaClienteLoginRequest;
import com.diskcerveja.manager.dto.ContaClienteRegistrarRequest;
import com.diskcerveja.manager.dto.ContaClienteResponse;
import com.diskcerveja.manager.dto.FretePublicoResponse;
import com.diskcerveja.manager.dto.InfinitePayWebhookRequest;
import com.diskcerveja.manager.dto.PedidoPublicoRequest;
import com.diskcerveja.manager.dto.PedidoPublicoResponse;
import com.diskcerveja.manager.service.CatalogoPublicoService;
import com.diskcerveja.manager.service.ContaClientePublicoService;
import com.diskcerveja.manager.service.PedidoPublicoService;
import com.diskcerveja.manager.service.ZonaEntregaService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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
    private final ZonaEntregaService zonaEntregaService;
    private final ContaClientePublicoService contaClientePublicoService;

    public PublicoController(
            CatalogoPublicoService catalogoPublicoService,
            PedidoPublicoService pedidoPublicoService,
            ZonaEntregaService zonaEntregaService,
            ContaClientePublicoService contaClientePublicoService) {
        this.catalogoPublicoService = catalogoPublicoService;
        this.pedidoPublicoService = pedidoPublicoService;
        this.zonaEntregaService = zonaEntregaService;
        this.contaClientePublicoService = contaClientePublicoService;
    }

    @GetMapping("/loja")
    public LojaPublicaDto loja() {
        return catalogoPublicoService.loja();
    }

    @GetMapping("/catalogo")
    public CatalogoPublicoResponse catalogo(@RequestParam(required = false) String q) {
        return catalogoPublicoService.catalogo(q);
    }

    @GetMapping("/frete")
    public FretePublicoResponse frete(
            @RequestParam(required = false) String cep, @RequestParam(required = false) String bairro) {
        return zonaEntregaService.cotar(cep, bairro);
    }

    @PostMapping("/conta/registrar")
    @ResponseStatus(HttpStatus.CREATED)
    public ContaClienteResponse registrarConta(@RequestBody @Valid ContaClienteRegistrarRequest req) {
        return contaClientePublicoService.registrar(req);
    }

    @PostMapping("/conta/login")
    public ContaClienteResponse loginConta(@RequestBody @Valid ContaClienteLoginRequest req) {
        return contaClientePublicoService.login(req);
    }

    @GetMapping("/conta/me")
    public ContaClienteResponse minhaConta(HttpServletRequest request) {
        return contaClientePublicoService.me(request.getHeader(HttpHeaders.AUTHORIZATION));
    }

    @PutMapping("/conta/me")
    public ContaClienteResponse atualizarConta(
            HttpServletRequest request, @RequestBody @Valid ContaClienteAtualizarRequest req) {
        return contaClientePublicoService.atualizar(request.getHeader(HttpHeaders.AUTHORIZATION), req);
    }

    @PostMapping("/pedidos")
    @ResponseStatus(HttpStatus.CREATED)
    public PedidoPublicoResponse criarPedido(
            @RequestBody @Valid PedidoPublicoRequest req, HttpServletRequest request) {
        return pedidoPublicoService.criar(req, request.getHeader(HttpHeaders.AUTHORIZATION));
    }

    @PostMapping("/infinitepay/webhook")
    public ResponseEntity<Map<String, String>> infinitepayWebhook(
            @RequestBody InfinitePayWebhookRequest body) {
        try {
            pedidoPublicoService.confirmarPagamentoWebhook(body);
            return ResponseEntity.ok(Map.of("status", "ok"));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("erro", ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.badRequest().body(Map.of("erro", "falha"));
        }
    }

    @PostMapping("/pedidos/{id}/confirmar-pagamento")
    public PedidoPublicoResponse confirmarPagamento(
            @PathVariable Long id, @RequestBody ConfirmarPagamentoPublicoRequest req) {
        return pedidoPublicoService.confirmarPagamentoRetorno(id, req);
    }

    @GetMapping("/pedidos/{id}/status-pagamento")
    public Map<String, Object> statusPagamento(@PathVariable Long id) {
        return pedidoPublicoService.statusPagamentoPublico(id);
    }
}
