package com.diskcerveja.manager.service;

import com.diskcerveja.manager.domain.entity.Usuario;
import com.diskcerveja.manager.dto.UsuarioDto;
import com.diskcerveja.manager.repository.UsuarioRepository;
import java.util.List;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public UsuarioService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public List<Usuario> listar() {
        return usuarioRepository.findAll();
    }

    @Transactional
    public Usuario salvar(UsuarioDto dto) {
        if (dto.id() == null && (dto.senha() == null || dto.senha().isBlank())) {
            throw new IllegalArgumentException("Senha obrigatória para novo usuário.");
        }
        Usuario u = dto.id() == null ? new Usuario() : usuarioRepository.findById(dto.id()).orElseThrow();
        if (usuarioRepository.existsByLoginIgnoreCase(dto.login())
                && (dto.id() == null || !dto.login().equalsIgnoreCase(u.getLogin()))) {
            throw new IllegalArgumentException("Login já em uso.");
        }
        u.setNome(dto.nome());
        u.setLogin(dto.login());
        if (dto.senha() != null && !dto.senha().isBlank()) {
            u.setSenha(passwordEncoder.encode(dto.senha()));
        }
        u.setPerfil(dto.perfil());
        u.setAtivo(dto.ativo());
        return usuarioRepository.save(u);
    }
}
