package com.diskcerveja.manager.web;

import com.diskcerveja.manager.domain.enums.PerfilUsuario;
import com.diskcerveja.manager.domain.enums.PeriodoPedido;
import com.diskcerveja.manager.domain.enums.StatusPedido;
import com.diskcerveja.manager.dto.PedidoPeriodoResponse;
import com.diskcerveja.manager.dto.PedidoMapper;
import com.diskcerveja.manager.dto.PedidoRequest;
import com.diskcerveja.manager.dto.PedidoResponse;
import com.diskcerveja.manager.dto.PedidoUpdateRequest;
import com.diskcerveja.manager.dto.StatusPedidoPatchRequest;
import com.diskcerveja.manager.security.SecurityUtils;
import com.diskcerveja.manager.service.PedidoRelatorioService;
import com.diskcerveja.manager.service.PedidoService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Locale;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/pedidos")
public class PedidoController {

    private final PedidoService pedidoService;
    private final PedidoRelatorioService pedidoRelatorioService;

    public PedidoController(PedidoService pedidoService, PedidoRelatorioService pedidoRelatorioService) {
        this.pedidoService = pedidoService;
        this.pedidoRelatorioService = pedidoRelatorioService;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Transactional(readOnly = true)
    public List<PedidoResponse> listar() {
        return pedidoService.listarRecentes().stream().map(PedidoMapper::toResponse).toList();
    }

    @GetMapping("/periodo")
    @PreAuthorize("isAuthenticated()")
    @Transactional(readOnly = true)
    public PedidoPeriodoResponse listarPorPeriodo(
            @RequestParam(value = "periodo", defaultValue = "DIA") String periodoParam) {
        PeriodoPedido periodo = parsePeriodo(periodoParam);
        return pedidoRelatorioService.listarPorPeriodo(periodo);
    }

    private static PeriodoPedido parsePeriodo(String raw) {
        if (raw == null || raw.isBlank()) {
            return PeriodoPedido.DIA;
        }
        try {
            return PeriodoPedido.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Período inválido. Use: DIA, SEMANA, MES ou ANO.");
        }
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public PedidoResponse buscar(@PathVariable Long id) {
        return PedidoMapper.toResponse(pedidoService.buscar(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR')")
    public ResponseEntity<PedidoResponse> criar(@RequestBody @Valid PedidoRequest request) {
        var p = pedidoService.criar(request, SecurityUtils.currentUser());
        return ResponseEntity.ok(PedidoMapper.toResponse(p));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR')")
    public PedidoResponse atualizar(@PathVariable Long id, @RequestBody @Valid PedidoUpdateRequest request) {
        return PedidoMapper.toResponse(pedidoService.atualizar(id, request));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR','ENTREGADOR')")
    public PedidoResponse status(@PathVariable Long id, @RequestBody @Valid StatusPedidoPatchRequest request) {
        if (SecurityUtils.currentUser().getPerfil() == PerfilUsuario.ENTREGADOR) {
            if (request.status() != StatusPedido.SAIU_ENTREGA && request.status() != StatusPedido.ENTREGUE) {
                throw new IllegalArgumentException("Entregador só pode avançar entrega (saiu / entregue).");
            }
        }
        return PedidoMapper.toResponse(pedidoService.mudarStatus(id, request.status(), SecurityUtils.currentUser()));
    }
}
