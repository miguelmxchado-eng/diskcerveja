package com.diskcerveja.manager.web;

import com.diskcerveja.manager.dto.CatalogoPublicoResponse;
import com.diskcerveja.manager.dto.CatalogoPublicoResponse.LojaPublicaDto;
import com.diskcerveja.manager.dto.ConfirmarPagamentoPublicoRequest;
import com.diskcerveja.manager.dto.InfinitePayWebhookRequest;
import com.diskcerveja.manager.dto.PedidoPublicoRequest;
import com.diskcerveja.manager.dto.PedidoPublicoResponse;
import com.diskcerveja.manager.service.CatalogoPublicoService;
import com.diskcerveja.manager.service.PedidoPublicoService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
        // Base URL só da config (não confiar em Host/X-Forwarded-* do cliente).
        return pedidoPublicoService.criar(req);
    }

    @PostMapping("/infinitepay/webhook")
    public ResponseEntity<Map<String, String>> infinitepayWebhook(
            @RequestBody InfinitePayWebhookRequest body) {
        try {
            pedidoPublicoService.confirmarPagamentoWebhook(body);
            return ResponseEntity.ok(Map.of("status", "ok"));
        } catch (IllegalArgumentException ex) {
            // Valor inválido: pede retry da InfinitePay
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

    /** Polling no retorno do checkout quando a InfinitePay não envia transaction_nsu/slug. */
    @GetMapping("/pedidos/{id}/status-pagamento")
    public Map<String, Object> statusPagamento(@PathVariable Long id) {
        return pedidoPublicoService.statusPagamentoPublico(id);
    }
}
