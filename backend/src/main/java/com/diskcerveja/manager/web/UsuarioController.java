package com.diskcerveja.manager.web;

import com.diskcerveja.manager.domain.entity.Usuario;
import com.diskcerveja.manager.dto.UsuarioDto;
import com.diskcerveja.manager.service.UsuarioService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/usuarios")
@PreAuthorize("hasRole('ADMIN')")
public class UsuarioController {

    private final UsuarioService usuarioService;

    public UsuarioController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @GetMapping
    public List<UsuarioDto> listar() {
        return usuarioService.listar().stream().map(UsuarioController::toDto).toList();
    }

    @PostMapping
    public UsuarioDto criar(@RequestBody @Valid UsuarioDto dto) {
        return toDto(usuarioService.salvar(dto));
    }

    @PutMapping("/{id}")
    public UsuarioDto atualizar(@PathVariable Long id, @RequestBody @Valid UsuarioDto dto) {
        return toDto(usuarioService.salvar(new UsuarioDto(
                id, dto.nome(), dto.login(), dto.senha(), dto.perfil(), dto.ativo())));
    }

    private static UsuarioDto toDto(Usuario u) {
        return new UsuarioDto(u.getId(), u.getNome(), u.getLogin(), null, u.getPerfil(), u.isAtivo());
    }
}
