package com.luxtrox.backend.controller;

import com.luxtrox.backend.dto.auth.AuthResponse;
import com.luxtrox.backend.dto.auth.LoginRequest;
import com.luxtrox.backend.dto.auth.RefreshTokenRequest;
import com.luxtrox.backend.dto.auth.RegisterRequest;
import com.luxtrox.backend.entity.PasswordResetToken;
import com.luxtrox.backend.entity.User;
import com.luxtrox.backend.exception.BusinessRuleException;
import com.luxtrox.backend.exception.ResourceNotFoundException;
import com.luxtrox.backend.repository.PasswordResetTokenRepository;
import com.luxtrox.backend.repository.UserRepository;
import com.luxtrox.backend.service.AuthService;
import com.luxtrox.backend.service.NotificationEmailService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/auth")
@Tag(name = "Auth", description = "Registro, login, refresh, logout y recuperacion de contrasena")
public class AuthController {

    private final AuthService authService;
    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final NotificationEmailService notificationEmailService;
    private final PasswordEncoder passwordEncoder;
    private final String frontendUrl;

    public AuthController(AuthService authService,
                           UserRepository userRepository,
                           PasswordResetTokenRepository tokenRepository,
                           NotificationEmailService notificationEmailService,
                           PasswordEncoder passwordEncoder,
                           @Value("${NOWPAYMENTS_SUCCESS_URL:http://localhost:3000/checkout/success}") String successUrl) {
        this.authService = authService;
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.notificationEmailService = notificationEmailService;
        this.passwordEncoder = passwordEncoder;
        // Extrae la base del frontend de la URL de success de NOWPayments
        // (ej. https://mi-app.vercel.app/checkout/success → https://mi-app.vercel.app)
        this.frontendUrl = successUrl.replaceAll("/checkout/success.*", "");
    }

    @PostMapping("/register")
    @Operation(summary = "Registrar un nuevo usuario")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    @PostMapping("/login")
    @Operation(summary = "Iniciar sesion")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Renovar el access token usando un refresh token valido")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(authService.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    @Operation(summary = "Revocar un refresh token (cerrar sesion)")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    record ForgotPasswordRequest(@Email @NotBlank String email) {}

    /**
     * Genera un token de reset y envia el correo. NUNCA confirma si el
     * email existe o no (respuesta identica en ambos casos) -- evita
     * enumerar usuarios validos por fuerza bruta.
     */
    @PostMapping("/forgot-password")
    @Transactional
    @Operation(summary = "Solicitar reset de contrasena -- envia correo si el email existe")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        Optional<User> userOpt = userRepository.findByEmail(request.email());
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            PasswordResetToken prt = tokenRepository.save(new PasswordResetToken(user));
            String resetUrl = frontendUrl + "/auth/reset-password?token=" + prt.getToken();
            try {
                notificationEmailService.sendPasswordResetEmail(user, resetUrl);
            } catch (Exception ignored) {
                // El correo fallo pero no lo revelamos al cliente
            }
        }
        return ResponseEntity.noContent().build();
    }

    record ResetPasswordRequest(
            @NotNull UUID token,
            @NotBlank @Size(min = 8, message = "La contrasena debe tener al menos 8 caracteres") String newPassword
    ) {}

    @PostMapping("/reset-password")
    @Transactional
    @Operation(summary = "Restablecer contrasena con un token valido (24h, uso unico)")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        PasswordResetToken prt = tokenRepository.findByToken(request.token())
                .orElseThrow(() -> new BusinessRuleException("Token invalido o expirado"));

        if (prt.isUsed())    throw new BusinessRuleException("Este token ya fue utilizado");
        if (prt.isExpired()) throw new BusinessRuleException("Este token ha expirado");

        User user = prt.getUser();
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);

        prt.markUsed();
        tokenRepository.save(prt);

        return ResponseEntity.noContent().build();
    }
}
