package com.diskcerveja.manager.web;

import com.diskcerveja.manager.domain.enums.FormaPagamento;
import com.diskcerveja.manager.domain.enums.StatusContaFinanceira;
import com.diskcerveja.manager.domain.enums.TipoContaFinanceira;
import com.diskcerveja.manager.dto.ContaFinanceiraDto;
import com.diskcerveja.manager.service.ContaFinanceiraService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/contas")
@PreAuthorize("hasAnyRole('ADMIN','OPERADOR')")
public class ContaFinanceiraController {

    private final ContaFinanceiraService service;

    public ContaFinanceiraController(ContaFinanceiraService service) {
        this.service = service;
    }

    @GetMapping
    public List<ContaFinanceiraDto> listar(
            @RequestParam TipoContaFinanceira tipo,
            @RequestParam(required = false) StatusContaFinanceira status,
            @RequestParam(value = "q", required = false) String q) {
        return service.listar(tipo, status, q);
    }

    @GetMapping("/{id}")
    public ContaFinanceiraDto buscar(@PathVariable Long id) {
        return service.buscar(id);
    }

    @PostMapping
    public ContaFinanceiraDto criar(@RequestBody @Valid ContaFinanceiraDto dto) {
        return service.salvar(new ContaFinanceiraDto(
                null,
                dto.tipo(),
                dto.descricao(),
                dto.pessoa(),
                dto.valor(),
                dto.vencimento(),
                StatusContaFinanceira.ABERTA,
                null,
                null,
                dto.observacao()));
    }

    @PutMapping("/{id}")
    public ContaFinanceiraDto atualizar(@PathVariable Long id, @RequestBody @Valid ContaFinanceiraDto dto) {
        return service.salvar(new ContaFinanceiraDto(
                id,
                dto.tipo(),
                dto.descricao(),
                dto.pessoa(),
                dto.valor(),
                dto.vencimento(),
                dto.status(),
                dto.dataPagamento(),
                dto.formaPagamento(),
                dto.observacao()));
    }

    @PostMapping("/{id}/quitar")
    public ContaFinanceiraDto quitar(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> body) {
        FormaPagamento forma = null;
        LocalDate data = null;
        if (body != null) {
            Object f = body.get("formaPagamento");
            if (f != null && !f.toString().isBlank()) {
                forma = FormaPagamento.valueOf(f.toString().trim().toUpperCase());
            }
            Object d = body.get("dataPagamento");
            if (d != null && !d.toString().isBlank()) {
                data = LocalDate.parse(d.toString());
            }
        }
        return service.quitar(id, forma, data);
    }

    @PostMapping("/{id}/cancelar")
    public ResponseEntity<ContaFinanceiraDto> cancelar(@PathVariable Long id) {
        return ResponseEntity.ok(service.cancelar(id));
    }
}
