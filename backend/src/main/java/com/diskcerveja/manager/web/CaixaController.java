package com.diskcerveja.manager.web;

import com.diskcerveja.manager.domain.entity.CaixaSessao;
import com.diskcerveja.manager.domain.entity.MovimentoCaixa;
import com.diskcerveja.manager.domain.enums.TipoMovimentoCaixa;
import com.diskcerveja.manager.dto.CaixaAberturaRequest;
import com.diskcerveja.manager.dto.CaixaFechamentoRequest;
import com.diskcerveja.manager.dto.CaixaSincronizarRequest;
import com.diskcerveja.manager.dto.CaixaSincronizarResponse;
import com.diskcerveja.manager.dto.CaixaSessaoResponse;
import com.diskcerveja.manager.dto.MovimentoCaixaRequest;
import com.diskcerveja.manager.dto.MovimentoCaixaResponse;
import com.diskcerveja.manager.security.SecurityUtils;
import com.diskcerveja.manager.service.CaixaSessaoService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/caixa")
public class CaixaController {

    private final CaixaSessaoService caixaSessaoService;

    public CaixaController(CaixaSessaoService caixaSessaoService) {
        this.caixaSessaoService = caixaSessaoService;
    }

    @GetMapping("/sessao")
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR')")
    public ResponseEntity<CaixaSessaoResponse> sessao() {
        CaixaSessao s = caixaSessaoService.sessaoAbertaHoje();
        if (s == null) {
            return ResponseEntity.noContent().build();
        }
        List<MovimentoCaixa> movs = caixaSessaoService.movimentosDoCaixaAberto();
        List<MovimentoCaixaResponse> mdtos = movs.stream()
                .map(m -> new MovimentoCaixaResponse(
                        m.getId(), m.getTipo(), m.getValor(), m.getDescricao(), m.getCreatedAt()))
                .toList();
        var resp = new CaixaSessaoResponse(
                s.getId(),
                s.getDataReferencia(),
                s.getStatus(),
                s.getHoraAbertura(),
                s.getHoraFechamento(),
                s.getValorAbertura(),
                s.getValorFechamento(),
                caixaSessaoService.saldoPrevisto(s),
                mdtos);
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/abertura")
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR')")
    public CaixaSessaoResponse abrir(@RequestBody @Valid CaixaAberturaRequest req) {
        CaixaSessao s = caixaSessaoService.abrir(SecurityUtils.currentUser(), req.valorAbertura());
        return new CaixaSessaoResponse(
                s.getId(),
                s.getDataReferencia(),
                s.getStatus(),
                s.getHoraAbertura(),
                s.getHoraFechamento(),
                s.getValorAbertura(),
                s.getValorFechamento(),
                caixaSessaoService.saldoPrevisto(s),
                List.of());
    }

    @PostMapping("/fechamento")
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR')")
    public CaixaSessaoResponse fechar(@RequestBody @Valid CaixaFechamentoRequest req) {
        CaixaSessao s = caixaSessaoService.fechar(SecurityUtils.currentUser(), req.valorFechamento());
        return new CaixaSessaoResponse(
                s.getId(),
                s.getDataReferencia(),
                s.getStatus(),
                s.getHoraAbertura(),
                s.getHoraFechamento(),
                s.getValorAbertura(),
                s.getValorFechamento(),
                caixaSessaoService.saldoPrevisto(s),
                List.of());
    }

    @PostMapping("/movimento")
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR')")
    public MovimentoCaixaResponse movimento(@RequestBody @Valid MovimentoCaixaRequest req) {
        if (req.tipo() == TipoMovimentoCaixa.ENTRADA_VENDA) {
            throw new IllegalArgumentException("Tipo de movimento inválido para lançamento manual.");
        }
        MovimentoCaixa m = caixaSessaoService.registrarSaida(req.tipo(), req.valor(), req.descricao());
        return new MovimentoCaixaResponse(
                m.getId(), m.getTipo(), m.getValor(), m.getDescricao(), m.getCreatedAt());
    }

    /**
     * Cria ou atualiza lançamentos ENTRADA_VENDA para pedidos ENTREGUE no intervalo (dia do pedido = data de
     * referência da sessão).
     */
    @PostMapping("/sincronizar-vendas")
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR')")
    public CaixaSincronizarResponse sincronizarVendas(@RequestBody @Valid CaixaSincronizarRequest req) {
        int n = caixaSessaoService.sincronizarVendasPedidos(req.inicio(), req.fim());
        return new CaixaSincronizarResponse(n);
    }
}
