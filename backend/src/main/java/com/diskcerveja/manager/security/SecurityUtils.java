package com.diskcerveja.manager.security;

import com.diskcerveja.manager.domain.entity.Usuario;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtils {

    private SecurityUtils() {}

    public static Usuario currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof SecurityUser su)) {
            throw new IllegalStateException("Usuário não autenticado.");
        }
        return su.getUsuario();
    }
}
