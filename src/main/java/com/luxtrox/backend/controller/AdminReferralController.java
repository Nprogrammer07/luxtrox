package com.luxtrox.backend.controller;

import com.luxtrox.backend.dto.referral.AdminReferralResponse;
import com.luxtrox.backend.entity.Referral;
import com.luxtrox.backend.entity.enums.ReferralStatus;
import com.luxtrox.backend.repository.CashbackTransactionRepository;
import com.luxtrox.backend.repository.ReferralRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/admin/referrals")
@Tag(name = "Admin - Referrals", description = "Listado de todos los referidos, de todos los referentes")
public class AdminReferralController {

    private final ReferralRepository referralRepository;
    private final CashbackTransactionRepository transactionRepository;

    public AdminReferralController(ReferralRepository referralRepository,
                                    CashbackTransactionRepository transactionRepository) {
        this.referralRepository = referralRepository;
        this.transactionRepository = transactionRepository;
    }

    @GetMapping
    @Transactional(readOnly = true)
    @Operation(summary = "Listar todos los referidos del sistema, de todos los referentes")
    public List<AdminReferralResponse> list() {
        return referralRepository.findAll().stream().map(this::toResponse).toList();
    }

    private AdminReferralResponse toResponse(Referral referral) {
        boolean stillPending = referral.getStatus() == ReferralStatus.PENDING_PURCHASE;
        var referred = referral.getReferred();

        return new AdminReferralResponse(
                referral.getId(),
                referral.getReferrer().getId(),
                referred.getId(),
                referred.getFullName(),
                referred.getEmail(),
                transactionRepository.sumAmountBySourceReferral(referral),
                stillPending ? "active" : "inactive",
                referral.getCreatedAt(),
                0L  // sin módulo Driver ya no hay posiciones que contar
        );
    }
}
