package com.luxtrox.backend.security;

import com.luxtrox.backend.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

/**
 * Genera y valida access tokens (JWT, autocontenidos, sin estado).
 *
 * Los refresh tokens NO son JWT -- son un string aleatorio opaco; solo
 * su HASH se guarda en la tabla refresh_tokens (ver
 * docs/domain-model.md 2.14). Asi un refresh token se puede revocar
 * de verdad (borrando/marcando la fila), algo que un JWT autocontenido
 * no permite sin una lista de revocacion aparte.
 */
@Service
public class JwtService {

    private final JwtProperties jwtProperties;
    private final SecretKey signingKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public JwtService(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        this.signingKey = Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(User user) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + jwtProperties.getAccessTokenExpirationMs());

        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("role", user.getRole().getName())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey)
                .compact();
    }

    public long getAccessTokenExpirationMs() {
        return jwtProperties.getAccessTokenExpirationMs();
    }

    public long getRefreshTokenExpirationMs() {
        return jwtProperties.getRefreshTokenExpirationMs();
    }

    /** Genera un refresh token opaco (no JWT) -- 256 bits aleatorios. */
    public String generateOpaqueRefreshToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * SHA-256 del refresh token -- es lo que se guarda en la tabla
     * refresh_tokens, nunca el valor real (ver docs/domain-model.md
     * 2.14). No hace falta BCrypt aqui: el token ya es aleatorio de
     * alta entropia (256 bits), a diferencia de una contrasena de
     * usuario que si necesita un hash lento contra fuerza bruta.
     */
    public String hashToken(String token) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }

    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public UUID extractUserId(String token) {
        return UUID.fromString(parseClaims(token).getSubject());
    }

    /** true solo si el token es valido (firma correcta y no expirado). */
    public boolean isAccessTokenValid(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}
