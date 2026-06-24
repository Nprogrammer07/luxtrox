package com.luxtrox.backend.service;

import com.luxtrox.backend.dto.auth.AuthResponse;
import com.luxtrox.backend.dto.auth.LoginRequest;
import com.luxtrox.backend.dto.auth.RegisterRequest;
import com.luxtrox.backend.entity.Referral;
import com.luxtrox.backend.entity.RefreshToken;
import com.luxtrox.backend.entity.Role;
import com.luxtrox.backend.entity.User;
import com.luxtrox.backend.exception.BusinessRuleException;
import com.luxtrox.backend.exception.InvalidRefreshTokenException;
import com.luxtrox.backend.exception.ResourceNotFoundException;
import com.luxtrox.backend.repository.ReferralRepository;
import com.luxtrox.backend.repository.RefreshTokenRepository;
import com.luxtrox.backend.repository.RoleRepository;
import com.luxtrox.backend.repository.UserRepository;
import com.luxtrox.backend.security.JwtService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.OffsetDateTime;

/**
 * Logica de registro/login/refresh/logout. La creacion del registro
 * de Referral aqui es solo el "bookkeeping" (se crea en estado
 * PENDING_PURCHASE) -- el pago real del bono de $100 es el algoritmo
 * de evaluarReferral()/pagarBono() de la Fase 6 (motor financiero),
 * que no vive aqui.
 */
@Service
public class AuthService {

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // sin I/O/0/1 ambiguos
    private static final int REFERRAL_CODE_LENGTH = 8;

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final ReferralRepository referralRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(UserRepository userRepository,
                        RoleRepository roleRepository,
                        ReferralRepository referralRepository,
                        RefreshTokenRepository refreshTokenRepository,
                        PasswordEncoder passwordEncoder,
                        JwtService jwtService,
                        AuthenticationManager authenticationManager) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.referralRepository = referralRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessRuleException("Ya existe una cuenta con ese email");
        }

        Role userRole = roleRepository.findByName("USER")
                .orElseThrow(() -> new IllegalStateException("Rol USER no existe -- revisar el seed de V1"));

        User newUser = new User(
                request.fullName(),
                request.email(),
                request.phone(),
                passwordEncoder.encode(request.password()),
                userRole,
                generateUniqueReferralCode()
        );

        // Si vino un codigo de referido, validarlo y enlazar -- pero
        // el bono de $100 NO se paga aqui (ver docs/domain-model.md 4.2,
        // Fase 6).
        if (request.referralCode() != null && !request.referralCode().isBlank()) {
            User referrer = userRepository.findByReferralCode(request.referralCode())
                    .orElseThrow(() -> new BusinessRuleException("El codigo de referido no existe"));
            newUser.setReferredBy(referrer);
        }

        User savedUser = userRepository.save(newUser);

        if (savedUser.getReferredBy() != null) {
            referralRepository.save(new Referral(
                    savedUser.getReferredBy(), savedUser, request.referralCode()
            ));
        }

        return buildAuthResponse(savedUser);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        // Lanza BadCredentialsException si el email/password no calzan
        // -- la captura GlobalExceptionHandler, no aqui.
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password())
        );

        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        return buildAuthResponse(user);
    }

    @Transactional
    public AuthResponse refresh(String rawRefreshToken) {
        String hash = jwtService.hashToken(rawRefreshToken);

        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new InvalidRefreshTokenException("Refresh token invalido"));

        if (stored.isRevoked()) {
            throw new InvalidRefreshTokenException("Refresh token revocado");
        }
        if (stored.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new InvalidRefreshTokenException("Refresh token expirado");
        }

        // Rotacion: el token usado se revoca y se emite uno nuevo --
        // si alguien reusa un refresh token ya consumido, esto permite
        // detectarlo (quedaria invalido para todos desde ese momento).
        stored.setRevoked(true);
        refreshTokenRepository.save(stored);

        return buildAuthResponse(stored.getUser());
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        String hash = jwtService.hashToken(rawRefreshToken);
        refreshTokenRepository.findByTokenHash(hash).ifPresent(token -> {
            token.setRevoked(true);
            refreshTokenRepository.save(token);
        });
    }

    private AuthResponse buildAuthResponse(User user) {
        String accessToken = jwtService.generateAccessToken(user);
        String rawRefreshToken = jwtService.generateOpaqueRefreshToken();

        RefreshToken refreshTokenEntity = new RefreshToken(
                user,
                jwtService.hashToken(rawRefreshToken),
                OffsetDateTime.now().plus(java.time.Duration.ofMillis(jwtService.getRefreshTokenExpirationMs()))
        );
        refreshTokenRepository.save(refreshTokenEntity);

        return new AuthResponse(
                accessToken,
                rawRefreshToken,
                "Bearer",
                jwtService.getAccessTokenExpirationMs(),
                new AuthResponse.UserSummary(
                        user.getId(),
                        user.getFullName(),
                        user.getEmail(),
                        user.getRole().getName(),
                        user.getReferralCode()
                )
        );
    }

    private String generateUniqueReferralCode() {
        String code;
        do {
            StringBuilder sb = new StringBuilder(REFERRAL_CODE_LENGTH);
            for (int i = 0; i < REFERRAL_CODE_LENGTH; i++) {
                sb.append(ALPHABET.charAt(secureRandom.nextInt(ALPHABET.length())));
            }
            code = sb.toString();
        } while (userRepository.findByReferralCode(code).isPresent());
        return code;
    }
}
