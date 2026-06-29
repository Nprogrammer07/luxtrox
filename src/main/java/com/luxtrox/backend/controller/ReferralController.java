package com.luxtrox.backend.controller;

import com.luxtrox.backend.dto.referral.ReferralBonusResponse;
import com.luxtrox.backend.dto.referral.ReferralResponse;
import com.luxtrox.backend.dto.referral.ReferralSummaryResponse;
import com.luxtrox.backend.entity.Referral;
import com.luxtrox.backend.entity.User;
import com.luxtrox.backend.entity.enums.ReferralStatus;
import com.luxtrox.backend.repository.CashbackTransactionRepository;
import com.luxtrox.backend.repository.InvestmentPositionRepository;
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
    private final InvestmentPositionRepository positionRepository;

    public ReferralController(ReferralRepository referralRepository,
                               CashbackTransactionRepository transactionRepository,
                               UserRepository userRepository,
                               InvestmentPositionRepository positionRepository) {
        this.referralRepository = referralRepository;
        this.transactionRepository = transactionRepository;
        this.userRepository = userRepository;
        this.positionRepository = positionRepository;
    }

    @GetMapping("/my-code")
    @Operation(summary = "Mi propio codigo de referido, para compartir")
    public ResponseEntity<String> myReferralCode(@AuthenticationPrincipal CustomUserPrincipal principal) {
        return ResponseEntity.ok(principal.getUser().getReferralCode());
    }

    @GetMapping("/summary")
    @Operation(summary = "Resumen de mi actividad de referidos (totales, activos, comision ganada)")
    public ReferralSummaryResponse summary(@AuthenticationPrincipal CustomUserPrincipal principal) {
        User user = principal.getUser();
        return new ReferralSummaryResponse(
                user.getReferralCode(),
                referralRepository.countByReferrer(user),
                referralRepository.countByReferrerAndStatus(user, ReferralStatus.PENDING_PURCHASE),
                transactionRepository.sumReferralBonusForUser(user),
                BigDecimal.ZERO
        );
    }

    /**
     * @Transactional aqui es necesario: CashbackTransaction.sourceReferral
     * es FetchType.LAZY, y toBonusResponse() lo lee despues de que
     * el repositorio ya devolvio. Mismo patron ya resuelto varias
     * veces antes en este proyecto (ver myReferrals() mas abajo).
     */
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

    /**
     * @Transactional aqui es necesario: referral.getReferred() es
     * FetchType.LAZY, y toResponse() lo lee DESPUES de que
     * referralRepository.findByReferrerAndStatus() ya devolvio (su
     * propia transaccion, mas corta, ya cerro para ese punto). Sin
     * esto, accederlo durante el mapeo tira LazyInitializationException
     * -- el mismo patron que CustomUserPrincipal.getAuthorities()
     * tenia con user.getRole() (ver ese comentario para el detalle
     * completo), encontrado aqui al escribir el primer test que de
     * verdad ejercitaba este endpoint.
     */
    @GetMapping
    @Transactional(readOnly = true)
    @Operation(summary = "Personas que he referido, y el estado de cada comision")
    public ResponseEntity<List<ReferralResponse>> myReferrals(
            @AuthenticationPrincipal CustomUserPrincipal principal) {

        // findByReferrerAndStatus exige un status -- como aqui se
        // quiere el historial completo sin filtrar, se consulta cada
        // estado posible y se combina, en vez de agregar un metodo
        // "findByReferrer" nuevo solo para este unico uso.
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
                positionRepository.countByUser(referred),
                referral.getQualifiedAt(),
                referral.getBonusPaidAt()
        );
    }
}