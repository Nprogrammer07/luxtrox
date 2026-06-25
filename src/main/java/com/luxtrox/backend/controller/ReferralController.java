package com.luxtrox.backend.controller;

import com.luxtrox.backend.dto.referral.ReferralResponse;
import com.luxtrox.backend.entity.Referral;
import com.luxtrox.backend.entity.enums.ReferralStatus;
import com.luxtrox.backend.repository.ReferralRepository;
import com.luxtrox.backend.security.CustomUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/referrals")
@Tag(name = "Referrals", description = "Comisiones por referidos del usuario autenticado")
public class ReferralController {

    private final ReferralRepository referralRepository;

    public ReferralController(ReferralRepository referralRepository) {
        this.referralRepository = referralRepository;
    }

    @GetMapping("/my-code")
    @Operation(summary = "Mi propio codigo de referido, para compartir")
    public ResponseEntity<String> myReferralCode(@AuthenticationPrincipal CustomUserPrincipal principal) {
        return ResponseEntity.ok(principal.getUser().getReferralCode());
    }

    @GetMapping
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
        return new ReferralResponse(
                referral.getId(),
                referral.getReferred().getFullName(),
                referral.getReferred().getEmail(),
                referral.getStatus(),
                referral.getQualifiedAt(),
                referral.getBonusPaidAt()
        );
    }
}
