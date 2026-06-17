package com.diskcerveja.manager.web;

import com.diskcerveja.manager.dto.LoginRequest;
import com.diskcerveja.manager.dto.TokenResponse;
import com.diskcerveja.manager.security.JwtTokenProvider;
import com.diskcerveja.manager.security.SecurityUser;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthController(AuthenticationManager authenticationManager, JwtTokenProvider jwtTokenProvider) {
        this.authenticationManager = authenticationManager;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@RequestBody @Valid LoginRequest request) {
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.login(), request.senha()));
        SecurityUser user = (SecurityUser) auth.getPrincipal();
        String token = jwtTokenProvider.createToken(
                user.getUsername(), user.getPerfil(), user.getUsuario().getId());
        return ResponseEntity.ok(new TokenResponse(
                token,
                user.getUsuario().getNome(),
                user.getUsuario().getLogin(),
                user.getUsuario().getPerfil(),
                user.getUsuario().getId()));
    }
}
