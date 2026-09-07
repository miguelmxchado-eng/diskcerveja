package com.diskcerveja.manager.security;

import com.diskcerveja.manager.domain.enums.PerfilUsuario;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenProvider {

    private final JwtProperties properties;

    public JwtTokenProvider(JwtProperties properties) {
        this.properties = properties;
    }

    public String createToken(String login, PerfilUsuario perfil, Long userId) {
        Date now = new Date();
        Date exp = new Date(now.getTime() + properties.getExpirationMs());
        return Jwts.builder()
                .subject(login)
                .claim("typ", "USUARIO")
                .claim("perfil", perfil.name())
                .claim("uid", userId)
                .issuedAt(now)
                .expiration(exp)
                .signWith(signingKey())
                .compact();
    }

    /** Token do cliente do cardápio (30 dias). */
    public String createClienteToken(Long clienteId, String telefoneDigits) {
        Date now = new Date();
        Date exp = new Date(now.getTime() + 30L * 24 * 60 * 60 * 1000);
        return Jwts.builder()
                .subject(telefoneDigits)
                .claim("typ", "CLIENTE")
                .claim("cid", clienteId)
                .issuedAt(now)
                .expiration(exp)
                .signWith(signingKey())
                .compact();
    }

    public boolean isClienteToken(Claims claims) {
        return claims != null && "CLIENTE".equals(claims.get("typ", String.class));
    }

    public Long clienteId(Claims claims) {
        Object cid = claims.get("cid");
        if (cid instanceof Number n) {
            return n.longValue();
        }
        if (cid instanceof String s && !s.isBlank()) {
            return Long.parseLong(s);
        }
        return null;
    }

    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(signingKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey signingKey() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(properties.getSecret().getBytes(StandardCharsets.UTF_8));
            return Keys.hmacShaKeyFor(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
