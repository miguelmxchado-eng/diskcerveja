package com.diskcerveja.manager.web;

import com.diskcerveja.manager.dto.ComboDto;
import com.diskcerveja.manager.dto.ComboRelatorioResponse;
import com.diskcerveja.manager.dto.ComboResponse;
import com.diskcerveja.manager.service.ComboService;
import jakarta.validation.Valid;
import java.util.List;
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
@RequestMapping("/api/combos")
public class ComboController {

    private final ComboService comboService;

    public ComboController(ComboService comboService) {
        this.comboService = comboService;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<ComboResponse> listar(
            @RequestParam(value = "somenteAtivos", defaultValue = "false") boolean somenteAtivos) {
        return comboService.listar(somenteAtivos);
    }

    @GetMapping("/ativos")
    @PreAuthorize("isAuthenticated()")
    public List<ComboResponse> ativos() {
        return comboService.listar(true);
    }

    @GetMapping("/relatorio")
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR')")
    public List<ComboRelatorioResponse> relatorio() {
        return comboService.relatorio();
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ComboResponse buscar(@PathVariable Long id) {
        return comboService.buscarResponse(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ComboResponse> criar(@RequestBody @Valid ComboDto dto) {
        ComboDto novo = new ComboDto(
                null,
                dto.nome(),
                dto.codigoBarras(),
                dto.codigoQr(),
                dto.categoria(),
                dto.descricao(),
                dto.imagem(),
                dto.precoVenda(),
                dto.ativo(),
                dto.itens());
        return ResponseEntity.ok(comboService.salvar(novo));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ComboResponse atualizar(@PathVariable Long id, @RequestBody @Valid ComboDto dto) {
        ComboDto alvo = new ComboDto(
                id,
                dto.nome(),
                dto.codigoBarras(),
                dto.codigoQr(),
                dto.categoria(),
                dto.descricao(),
                dto.imagem(),
                dto.precoVenda(),
                dto.ativo(),
                dto.itens());
        return comboService.salvar(alvo);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        comboService.excluir(id);
        return ResponseEntity.noContent().build();
    }
}
