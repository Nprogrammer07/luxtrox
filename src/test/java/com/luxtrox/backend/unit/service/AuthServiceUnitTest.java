package com.luxtrox.backend.unit.service;

import com.luxtrox.backend.dto.auth.AuthResponse;
import com.luxtrox.backend.dto.auth.LoginRequest;
import com.luxtrox.backend.dto.auth.RegisterRequest;
import com.luxtrox.backend.entity.RefreshToken;
import com.luxtrox.backend.entity.Role;
import com.luxtrox.backend.entity.User;
import com.luxtrox.backend.exception.BusinessRuleException;
import com.luxtrox.backend.exception.InvalidRefreshTokenException;
import com.luxtrox.backend.repository.ReferralRepository;
import com.luxtrox.backend.repository.RefreshTokenRepository;
import com.luxtrox.backend.repository.RoleRepository;
import com.luxtrox.backend.repository.UserRepository;
import com.luxtrox.backend.security.JwtService;
import com.luxtrox.backend.service.AuditService;
import com.luxtrox.backend.service.AuthService;
import com.luxtrox.backend.service.NotificationEmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unitario puro -- todas las dependencias mockeadas con Mockito, sin
 * Spring context, sin Docker, sin base de datos. Complementa (no
 * reemplaza) a AuthServiceTest (Testcontainers, en
 * com.luxtrox.backend.service) -- ese prueba el flujo CONTRA una base
 * real; este prueba que AuthService llama a sus dependencias
 * EXACTAMENTE como se espera, y cubre rutas de error que son
 * incomodas de forzar con una base real (ej. un repositorio que lanza
 * una excepcion inesperada).
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceUnitTest {

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private ReferralRepository referralRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private NotificationEmailService notificationEmailService;
    @Mock private AuditService auditService;

    private AuthService authService;

    private Role userRole;
    private User savedUser;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, roleRepository, referralRepository,
                refreshTokenRepository, passwordEncoder, jwtService, authenticationManager,
                notificationEmailService, auditService);

        userRole = new Role("USER", "Usuario estandar");
        savedUser = new User("Carlos Mendoza", "carlos@example.com", "+1",
                "hashed-password", userRole, "CARLOS01");
        setId(savedUser, UUID.randomUUID());

        // Comunes a casi todos los tests de register/login/refresh --
        // se sobreescriben en los tests que necesitan algo distinto.
        lenient().when(jwtService.generateAccessToken(any())).thenReturn("fake-access-token");
        lenient().when(jwtService.generateOpaqueRefreshToken()).thenReturn("fake-raw-refresh-token");
        lenient().when(jwtService.hashToken(anyString())).thenReturn("fake-token-hash");
        lenient().when(jwtService.getAccessTokenExpirationMs()).thenReturn(3_600_000L);
        lenient().when(jwtService.getRefreshTokenExpirationMs()).thenReturn(604_800_000L);
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

    // ---------- register() ----------

    @Test
    void register_happyPath_savesUserAndReturnsTokens() {
        RegisterRequest request = new RegisterRequest("Carlos Mendoza", "carlos@example.com",
                "+1", "password123", null);

        when(userRepository.existsByEmail("carlos@example.com")).thenReturn(false);
        when(roleRepository.findByName("USER")).thenReturn(Optional.of(userRole));
        when(passwordEncoder.encode("password123")).thenReturn("hashed-password");
        when(userRepository.findByReferralCode(anyString())).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        AuthResponse response = authService.register(request);

        assertThat(response.accessToken()).isEqualTo("fake-access-token");
        assertThat(response.user().email()).isEqualTo("carlos@example.com");
        verify(userRepository).save(any(User.class));
        verify(refreshTokenRepository).save(any(RefreshToken.class));
        verify(notificationEmailService).sendWelcomeEmail(any());
        // Sin codigo de referido -- nunca debe tocar la tabla de referrals.
        verify(referralRepository, never()).save(any());
    }

    @Test
    void register_duplicateEmail_throwsBeforeTouchingAnythingElse() {
        RegisterRequest request = new RegisterRequest("Carlos", "ya-existe@example.com", "+1", "password123", null);
        when(userRepository.existsByEmail("ya-existe@example.com")).thenReturn(true);

        assertThrows(BusinessRuleException.class, () -> authService.register(request));

        verify(userRepository, never()).save(any());
        verifyNoInteractions(passwordEncoder, jwtService, notificationEmailService);
    }

    @Test
    void register_withValidReferralCode_linksReferrerAndCreatesReferralRow() {
        Role referrerRole = new Role("USER", "Usuario estandar");
        User referrer = new User("Referente", "referente@example.com", "+1", "hash", referrerRole, "REFCODE1");
        setId(referrer, UUID.randomUUID());

        RegisterRequest request = new RegisterRequest("Carlos", "nuevo@example.com", "+1",
                "password123", "REFCODE1");

        when(userRepository.existsByEmail("nuevo@example.com")).thenReturn(false);
        when(roleRepository.findByName("USER")).thenReturn(Optional.of(userRole));
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        // generateUniqueReferralCode() TAMBIEN llama a findByReferralCode,
        // pero con un codigo aleatorio nuevo (para el usuario que se
        // registra) -- el catch-all cubre esa llamada; el stub
        // especifico de abajo gana solo para "REFCODE1" (la busqueda
        // del referente).
        when(userRepository.findByReferralCode(anyString())).thenReturn(Optional.empty());
        when(userRepository.findByReferralCode("REFCODE1")).thenReturn(Optional.of(referrer));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            setId(u, UUID.randomUUID());
            return u;
        });

        authService.register(request);

        ArgumentCaptor<User> savedCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(savedCaptor.capture());
        assertThat(savedCaptor.getValue().getReferredBy()).isEqualTo(referrer);
        verify(referralRepository).save(any());
    }

    @Test
    void register_withNonExistentReferralCode_throwsAndNeverSavesUser() {
        RegisterRequest request = new RegisterRequest("Carlos", "nuevo@example.com", "+1",
                "password123", "CODIGOFALSO");

        when(userRepository.existsByEmail("nuevo@example.com")).thenReturn(false);
        when(roleRepository.findByName("USER")).thenReturn(Optional.of(userRole));
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        // Aqui CUALQUIER codigo (el aleatorio del nuevo usuario, Y
        // "CODIGOFALSO") debe devolver empty() -- un solo catch-all
        // basta, no hace falta un stub especifico aparte.
        when(userRepository.findByReferralCode(anyString())).thenReturn(Optional.empty());

        assertThrows(BusinessRuleException.class, () -> authService.register(request));

        verify(userRepository, never()).save(any());
    }

    @Test
    void register_referralCodeCollision_retriesUntilUnique() {
        RegisterRequest request = new RegisterRequest("Carlos", "carlos@example.com", "+1", "password123", null);

        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(roleRepository.findByName("USER")).thenReturn(Optional.of(userRole));
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        // El codigo generado choca con uno existente la PRIMERA vez,
        // la segunda ya no -- debe reintentar, nunca fallar ni colarse
        // un codigo duplicado.
        when(userRepository.findByReferralCode(anyString()))
                .thenReturn(Optional.of(savedUser))
                .thenReturn(Optional.empty());

        authService.register(request);

        verify(userRepository, times(2)).findByReferralCode(anyString());
    }

    @Test
    void register_emailSendingFails_doesNotPreventRegistration() {
        RegisterRequest request = new RegisterRequest("Carlos", "carlos@example.com", "+1", "password123", null);

        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(roleRepository.findByName("USER")).thenReturn(Optional.of(userRole));
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(userRepository.findByReferralCode(anyString())).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        doThrow(new RuntimeException("Resend caido")).when(notificationEmailService).sendWelcomeEmail(any());

        AuthResponse response = authService.register(request);

        assertThat(response.accessToken()).isEqualTo("fake-access-token");
        verify(auditService).recordSystemAction(eq("User"), any(), eq("WELCOME_EMAIL_FAILED"), any(), any());
    }

    // ---------- login() ----------

    @Test
    void login_happyPath_authenticatesAndReturnsTokens() {
        LoginRequest request = new LoginRequest("carlos@example.com", "password123");
        when(userRepository.findByEmail("carlos@example.com")).thenReturn(Optional.of(savedUser));

        AuthResponse response = authService.login(request);

        assertThat(response.user().email()).isEqualTo("carlos@example.com");
        verify(authenticationManager).authenticate(argThat(token ->
                token.getName().equals("carlos@example.com")));
    }

    // ---------- refresh() ----------

    @Test
    void refresh_validToken_revokesOldAndIssuesNew() {
        RefreshToken stored = new RefreshToken(savedUser, "old-hash", OffsetDateTime.now().plusDays(1));
        when(jwtService.hashToken("raw-token")).thenReturn("old-hash");
        when(refreshTokenRepository.findByTokenHash("old-hash")).thenReturn(Optional.of(stored));

        authService.refresh("raw-token");

        assertThat(stored.isRevoked()).isTrue();
        verify(refreshTokenRepository, times(2)).save(any(RefreshToken.class)); // el viejo revocado + el nuevo
    }

    @Test
    void refresh_unknownToken_throwsInvalidRefreshTokenException() {
        when(jwtService.hashToken(anyString())).thenReturn("some-hash");
        when(refreshTokenRepository.findByTokenHash("some-hash")).thenReturn(Optional.empty());

        assertThrows(InvalidRefreshTokenException.class, () -> authService.refresh("nope"));
    }

    @Test
    void refresh_revokedToken_throwsInvalidRefreshTokenException() {
        RefreshToken stored = new RefreshToken(savedUser, "hash", OffsetDateTime.now().plusDays(1));
        stored.setRevoked(true);
        when(jwtService.hashToken(anyString())).thenReturn("hash");
        when(refreshTokenRepository.findByTokenHash("hash")).thenReturn(Optional.of(stored));

        assertThrows(InvalidRefreshTokenException.class, () -> authService.refresh("raw"));
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void refresh_expiredToken_throwsInvalidRefreshTokenException() {
        RefreshToken stored = new RefreshToken(savedUser, "hash", OffsetDateTime.now().minusSeconds(1));
        when(jwtService.hashToken(anyString())).thenReturn("hash");
        when(refreshTokenRepository.findByTokenHash("hash")).thenReturn(Optional.of(stored));

        assertThrows(InvalidRefreshTokenException.class, () -> authService.refresh("raw"));
    }

    // ---------- logout() ----------

    @Test
    void logout_existingToken_marksItRevoked() {
        RefreshToken stored = new RefreshToken(savedUser, "hash", OffsetDateTime.now().plusDays(1));
        when(jwtService.hashToken("raw")).thenReturn("hash");
        when(refreshTokenRepository.findByTokenHash("hash")).thenReturn(Optional.of(stored));

        authService.logout("raw");

        assertThat(stored.isRevoked()).isTrue();
        verify(refreshTokenRepository).save(stored);
    }

    @Test
    void logout_nonExistentToken_doesNothingAndNeverThrows() {
        when(jwtService.hashToken(anyString())).thenReturn("hash");
        when(refreshTokenRepository.findByTokenHash("hash")).thenReturn(Optional.empty());

        authService.logout("raw"); // no debe lanzar nada

        verify(refreshTokenRepository, never()).save(any());
    }
}