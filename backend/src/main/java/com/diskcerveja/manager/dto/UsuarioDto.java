package com.diskcerveja.manager.dto;

import com.diskcerveja.manager.domain.enums.PerfilUsuario;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UsuarioDto(Long id, @NotBlank String nome, @NotBlank String login, String senha, @NotNull PerfilUsuario perfil, boolean ativo) {}
