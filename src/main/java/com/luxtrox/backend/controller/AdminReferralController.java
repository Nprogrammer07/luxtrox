package com.luxtrox.backend.controller;

import com.luxtrox.backend.dto.referral.AdminReferralResponse;
import com.luxtrox.backend.entity.Referral;
import com.luxtrox.backend.entity.enums.ReferralStatus;
import com.luxtrox.backend.repository.CashbackTransactionRepository;
import com.luxtrox.backend.repository.InvestmentPositionRepository;
import com.luxtrox.backend.repository.ReferralRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * No existia ningun endpoint de admin para referidos -- el frontend
 * (Next.js) ya tenia /admin/referrals definido de forma especulativa
 * antes de que este backend existiera.
 *
 * @Transactional en list() a proposito: referral.getReferrer()/
 * .getReferred() son ambos FetchType.LAZY, y el mapeo a DTO tiene que
 * correr DENTRO de la misma transaccion que cargo la entidad (mismo
 * problema, ya resuelto varias veces antes en este proyecto -- ver
 * UserService/CashbackQueryService para el detalle completo del por
 * que).
 */
@RestController
@RequestMapping("/admin/referrals")
@Tag(name = "Admin - Referrals", description = "Listado de todos los referidos, de todos los referentes")
public class AdminReferralController {

    private final ReferralRepository referralRepository;
    private final CashbackTransactionRepository transactionRepository;
    private final InvestmentPositionRepository positionRepository;

    public AdminReferralController(ReferralRepository referralRepository,
                                    CashbackTransactionRepository transactionRepository,
                                    InvestmentPositionRepository positionRepository) {
        this.referralRepository = referralRepository;
        this.transactionRepository = transactionRepository;
        this.positionRepository = positionRepository;
    }

    @GetMapping
    @Transactional(readOnly = true)
    @Operation(summary = "Listar todos los referidos del sistema, de todos los referentes")
    public List<AdminReferralResponse> list() {
        return referralRepository.findAll().stream().map(this::toResponse).toList();
    }

    /**
     * status: ver AdminReferralResponse para la interpretacion
     * PENDING_PURCHASE -> "active" / RESOLVED -> "inactive" (y por
     * que el enum de 4 valores del backend se reduce a ese binario).
     */
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
                positionRepository.countByUser(referred)
        );
    }
}
