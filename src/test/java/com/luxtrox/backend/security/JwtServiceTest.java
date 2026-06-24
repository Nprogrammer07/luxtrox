package com.luxtrox.backend.security;

import com.luxtrox.backend.entity.Role;
import com.luxtrox.backend.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Test unitario puro -- sin Spring context, sin Docker. Solo prueba
 * la logica de JwtService en aislamiento (generar/parsear/validar
 * tokens, hashear refresh tokens).
 */
class JwtServiceTest {

    private JwtService jwtService;
    private User testUser;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties();
        // Clave de prueba de 32+ bytes -- HS256 exige minimo 256 bits,
        // de lo contrario Keys.hmacShaKeyFor lanza WeakKeyException.
        properties.setSecret("test-secret-key-must-be-at-least-32-bytes-long-for-hs256");
        properties.setAccessTokenExpirationMs(3_600_000L);
        properties.setRefreshTokenExpirationMs(604_800_000L);
        jwtService = new JwtService(properties);

        Role role = new Role("USER", "Usuario estandar");
        testUser = new User("Carlos Mendoza", "carlos@example.com", "+1", "hash", role, "CARLOS001");
        setId(testUser, UUID.randomUUID());
    }

    private void setId(User user, UUID id) {
        try {
            var field = User.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(user, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void generatesATokenThatIsValidAndContainsTheRightUserId() {
        String token = jwtService.generateAccessToken(testUser);

        assertThat(jwtService.isAccessTokenValid(token)).isTrue();
        assertThat(jwtService.extractUserId(token)).isEqualTo(testUser.getId());
    }

    @Test
    void rejectsATokenSignedWithADifferentSecret() {
        String token = jwtService.generateAccessToken(testUser);

        JwtProperties otherProperties = new JwtProperties();
        otherProperties.setSecret("a-completely-different-secret-key-32-bytes-minimum-too");
        otherProperties.setAccessTokenExpirationMs(3_600_000L);
        JwtService otherJwtService = new JwtService(otherProperties);

        assertThat(otherJwtService.isAccessTokenValid(token)).isFalse();
    }

    @Test
    void rejectsAnExpiredToken() {
        JwtProperties expiredProperties = new JwtProperties();
        expiredProperties.setSecret("test-secret-key-must-be-at-least-32-bytes-long-for-hs256");
        expiredProperties.setAccessTokenExpirationMs(-1000L); // ya expirado al generarse
        JwtService expiredJwtService = new JwtService(expiredProperties);

        String expiredToken = expiredJwtService.generateAccessToken(testUser);

        assertThat(expiredJwtService.isAccessTokenValid(expiredToken)).isFalse();
    }

    @Test
    void rejectsAGarbageToken() {
        assertThat(jwtService.isAccessTokenValid("esto-no-es-un-jwt")).isFalse();
    }

    @Test
    void opaqueRefreshTokensAreUniqueAndHighEntropy() {
        String token1 = jwtService.generateOpaqueRefreshToken();
        String token2 = jwtService.generateOpaqueRefreshToken();

        assertThat(token1).isNotEqualTo(token2);
        assertThat(token1.length()).isGreaterThanOrEqualTo(32);
    }

    @Test
    void hashingTheSameTokenAlwaysProducesTheSameHash() {
        String token = jwtService.generateOpaqueRefreshToken();

        assertThat(jwtService.hashToken(token)).isEqualTo(jwtService.hashToken(token));
    }

    @Test
    void hashingDifferentTokensProducesDifferentHashes() {
        String token1 = jwtService.generateOpaqueRefreshToken();
        String token2 = jwtService.generateOpaqueRefreshToken();

        assertThat(jwtService.hashToken(token1)).isNotEqualTo(jwtService.hashToken(token2));
    }

    @Test
    void rejectsASecretShorterThan32Bytes() {
        JwtProperties weakProperties = new JwtProperties();
        weakProperties.setSecret("muy-corto");

        assertThrows(Exception.class, () -> new JwtService(weakProperties));
    }
}
