package com.diskcerveja.manager.dto;

import com.diskcerveja.manager.domain.enums.PerfilUsuario;

public record TokenResponse(String token, String nome, String login, PerfilUsuario perfil, Long usuarioId) {}
