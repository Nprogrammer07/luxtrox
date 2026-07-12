package com.luxtrox.backend.controller;

import com.luxtrox.backend.dto.referral.ReferralBonusResponse;
import com.luxtrox.backend.dto.referral.ReferralResponse;
import com.luxtrox.backend.dto.referral.ReferralSummaryResponse;
import com.luxtrox.backend.entity.Referral;
import com.luxtrox.backend.entity.User;
import com.luxtrox.backend.entity.enums.ReferralStatus;
import com.luxtrox.backend.repository.CashbackTransactionRepository;
import com.luxtrox.backend.repository.ReferralRepository;
import com.luxtrox.backend.repository.UserRepository;
import com.luxtrox.backend.security.CustomUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/referrals")
@Tag(name = "Referrals", description = "Comisiones por referidos del usuario autenticado")
public class ReferralController {

    private final ReferralRepository referralRepository;
    private final CashbackTransactionRepository transactionRepository;
    private final UserRepository userRepository;

    public ReferralController(ReferralRepository referralRepository,
                               CashbackTransactionRepository transactionRepository,
                               UserRepository userRepository) {
        this.referralRepository = referralRepository;
        this.transactionRepository = transactionRepository;
        this.userRepository = userRepository;
    }

    @GetMapping("/my-code")
    @Operation(summary = "Devuelve el codigo de referido propio del usuario autenticado")
    public String myReferralCode(@AuthenticationPrincipal CustomUserPrincipal principal) {
        return principal.getUser().getReferralCode();
    }

    /**
     * activeReferrals = referidos en PENDING_PURCHASE (aun activos, no
     * han comprado). totalBonusEarned = suma de todas las comisiones
     * de referido recibidas. pendingBonus siempre 0 (ver
     * ReferralSummaryResponse para el por que).
     */
    @GetMapping("/summary")
    @Transactional(readOnly = true)
    @Operation(summary = "Resumen de referidos del usuario: codigo, totales y comisiones")
    public ReferralSummaryResponse summary(@AuthenticationPrincipal CustomUserPrincipal principal) {
        User user = principal.getUser();

        long total = 0;
        long active = 0;
        for (ReferralStatus status : ReferralStatus.values()) {
            List<Referral> byStatus = referralRepository.findByReferrerAndStatus(user, status);
            total += byStatus.size();
            if (status == ReferralStatus.PENDING_PURCHASE) {
                active += byStatus.size();
            }
        }

        BigDecimal totalBonusEarned = transactionRepository.sumReferralBonusForUser(user);

        return new ReferralSummaryResponse(
                user.getReferralCode(),
                total,
                active,
                totalBonusEarned,
                BigDecimal.ZERO
        );
    }

    @GetMapping("/bonuses")
    @Transactional(readOnly = true)
    @Operation(summary = "Historial de comisiones de referido ya pagadas")
    public List<ReferralBonusResponse> bonuses(@AuthenticationPrincipal CustomUserPrincipal principal) {
        return transactionRepository.findReferralBonusesForUser(principal.getUser()).stream()
                .map(tx -> new ReferralBonusResponse(
                        tx.getId(),
                        tx.getSourceReferral() != null ? tx.getSourceReferral().getId() : null,
                        tx.getAmount(),
                        "paid",
                        "Comision por referido",
                        tx.getCreatedAt()))
                .toList();
    }

    @GetMapping("/validate/{code}")
    @Operation(summary = "Validar si un codigo de referido existe, antes de usarlo al registrarse")
    public Map<String, Object> validate(@PathVariable String code) {
        return userRepository.findByReferralCode(code)
                .<Map<String, Object>>map(u -> Map.of("valid", true, "ownerName", u.getFullName()))
                .orElseGet(() -> Map.of("valid", false));
    }

    @GetMapping
    @Transactional(readOnly = true)
    @Operation(summary = "Personas que he referido, y el estado de cada comision")
    public ResponseEntity<List<ReferralResponse>> myReferrals(
            @AuthenticationPrincipal CustomUserPrincipal principal) {
        List<Referral> all = Arrays.stream(ReferralStatus.values())
                .flatMap(status -> referralRepository
                        .findByReferrerAndStatus(principal.getUser(), status).stream())
                .toList();
        List<ReferralResponse> response = all.stream().map(this::toResponse).toList();
        return ResponseEntity.ok(response);
    }

    private ReferralResponse toResponse(Referral referral) {
        var referred = referral.getReferred();
        return new ReferralResponse(
                referral.getId(),
                referred.getId(),
                referred.getFullName(),
                referred.getEmail(),
                transactionRepository.sumAmountBySourceReferral(referral),
                referral.getStatus(),
                0L,
                referral.getQualifiedAt(),
                referral.getBonusPaidAt()
        );
    }
}