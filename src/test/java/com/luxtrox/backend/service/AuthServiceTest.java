package com.luxtrox.backend.service;

import com.luxtrox.backend.dto.auth.AuthResponse;
import com.luxtrox.backend.dto.auth.LoginRequest;
import com.luxtrox.backend.dto.auth.RegisterRequest;
import com.luxtrox.backend.exception.BusinessRuleException;
import com.luxtrox.backend.exception.InvalidRefreshTokenException;
import com.luxtrox.backend.repository.AbstractIntegrationTest;
import com.luxtrox.backend.repository.ReferralRepository;
import com.luxtrox.backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.BadCredentialsException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Prueba el flujo completo de autenticacion contra un Postgres real
 * (Testcontainers), incluyendo el enlace con Referral al registrarse
 * con un codigo valido -- el bookkeeping que describe
 * docs/domain-model.md 4.2 (el PAGO del bono es Fase 6, no esto).
 */
class AuthServiceTest extends AbstractIntegrationTest {

    @Autowired
    private AuthService authService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ReferralRepository referralRepository;

    private RegisterRequest baseRegisterRequest(String email, String referralCode) {
        return new RegisterRequest("Carlos Mendoza", email, "+57300", "password123", referralCode);
    }

    @Test
    void registerCreatesUserAndReturnsValidTokens() {
        AuthResponse response = authService.register(baseRegisterRequest("nuevo1@example.com", null));

        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.refreshToken()).isNotBlank();
        assertThat(response.user().email()).isEqualTo("nuevo1@example.com");
        assertThat(response.user().role()).isEqualTo("USER");
        assertThat(response.user().referralCode()).hasSize(8);
        assertThat(userRepository.existsByEmail("nuevo1@example.com")).isTrue();
    }

    @Test
    void registerRejectsADuplicateEmail() {
        authService.register(baseRegisterRequest("duplicado@example.com", null));

        assertThrows(BusinessRuleException.class,
                () -> authService.register(baseRegisterRequest("duplicado@example.com", null)));
    }

    @Test
    void registerWithAValidReferralCodeLinksReferrerAndCreatesReferralRecord() {
        AuthResponse referrerResponse = authService.register(baseRegisterRequest("referente@example.com", null));
        String referrerCode = referrerResponse.user().referralCode();

        AuthResponse referredResponse = authService.register(
                baseRegisterRequest("referido@example.com", referrerCode));

        var referredUser = userRepository.findByEmail("referido@example.com").orElseThrow();
        assertThat(referredUser.getReferredBy()).isNotNull();
        assertThat(referredUser.getReferredBy().getEmail()).isEqualTo("referente@example.com");

        var referral = referralRepository.findByReferred(referredUser);
        assertThat(referral).isPresent();
        assertThat(referral.get().getReferralCodeUsed()).isEqualTo(referrerCode);
    }

    @Test
    void registerRejectsANonExistentReferralCode() {
        assertThrows(BusinessRuleException.class,
                () -> authService.register(baseRegisterRequest("otro@example.com", "CODIGOFALSO")));
    }

    @Test
    void loginSucceedsWithCorrectCredentials() {
        authService.register(baseRegisterRequest("login1@example.com", null));

        AuthResponse response = authService.login(new LoginRequest("login1@example.com", "password123"));

        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.user().email()).isEqualTo("login1@example.com");
    }

    @Test
    void loginFailsWithWrongPassword() {
        authService.register(baseRegisterRequest("login2@example.com", null));

        assertThrows(BadCredentialsException.class,
                () -> authService.login(new LoginRequest("login2@example.com", "contrasena-incorrecta")));
    }

    @Test
    void refreshIssuesNewTokensAndRevokesTheOldRefreshToken() {
        AuthResponse original = authService.register(baseRegisterRequest("refresh1@example.com", null));

        AuthResponse refreshed = authService.refresh(original.refreshToken());

        assertThat(refreshed.accessToken()).isNotBlank();
        assertThat(refreshed.refreshToken()).isNotEqualTo(original.refreshToken());

        // El token original ya esta revocado -- reusarlo debe fallar.
        assertThrows(InvalidRefreshTokenException.class,
                () -> authService.refresh(original.refreshToken()));
    }

    @Test
    void refreshRejectsAGarbageToken() {
        assertThrows(InvalidRefreshTokenException.class,
                () -> authService.refresh("este-token-no-existe"));
    }

    @Test
    void logoutRevokesTheRefreshTokenSoItCanNoLongerBeUsed() {
        AuthResponse response = authService.register(baseRegisterRequest("logout1@example.com", null));

        authService.logout(response.refreshToken());

        assertThrows(InvalidRefreshTokenException.class,
                () -> authService.refresh(response.refreshToken()));
    }
}
