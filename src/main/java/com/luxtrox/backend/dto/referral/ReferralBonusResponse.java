package com.luxtrox.backend.dto.referral;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * status siempre vale "paid" -- mismo motivo que pendingBonus en
 * ReferralSummaryResponse: la evaluacion de una comision es unica e
 * inmediata, no existe un estado "pendiente" intermedio en este
 * backend.
 *
 * description se mantiene generico a proposito ("Comision por
 * referido") -- llegar al plan (Driver/Zenith) que origino la
 * comision exigiria atravesar sourceReferral.triggeringPurchase.planType,
 * un tercer salto LAZY encima de los dos que ya tiene este DTO
 * (transaction.sourceReferral, sourceReferral.referred); no se
 * considero que el detalle valiera ese riesgo/complejidad adicional.
 */
public record ReferralBonusResponse(
        UUID id,
        UUID referralId,
        BigDecimal amount,
        String status,
        String description,
        OffsetDateTime createdAt
) {
}