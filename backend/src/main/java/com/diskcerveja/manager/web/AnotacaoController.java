package com.diskcerveja.manager.web;

import com.diskcerveja.manager.dto.AnotacaoDto;
import com.diskcerveja.manager.security.SecurityUtils;
import com.diskcerveja.manager.service.AnotacaoService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/anotacoes")
@PreAuthorize("hasAnyRole('ADMIN','OPERADOR')")
public class AnotacaoController {

    private final AnotacaoService service;

    public AnotacaoController(AnotacaoService service) {
        this.service = service;
    }

    @GetMapping
    public List<AnotacaoDto> listar(
            @RequestParam(value = "concluidas", defaultValue = "false") boolean concluidas,
            @RequestParam(value = "q", required = false) String q) {
        return service.listar(concluidas, q);
    }

    @GetMapping("/contagem")
    public Map<String, Long> contagem() {
        return service.contagem();
    }

    @PostMapping
    public AnotacaoDto criar(@RequestBody @Valid AnotacaoDto dto) {
        return service.criar(dto, SecurityUtils.currentUser().getNome());
    }

    @PutMapping("/{id}")
    public AnotacaoDto atualizar(@PathVariable Long id, @RequestBody @Valid AnotacaoDto dto) {
        return service.atualizar(id, dto);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        service.excluir(id);
        return ResponseEntity.noContent().build();
    }
}
