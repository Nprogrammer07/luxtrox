package com.luxtrox.backend.dto.referral;

import com.luxtrox.backend.entity.enums.ReferralStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * referredUserId/bonusAmount/seminarsCount se agregaron despues
 * (integracion con frontend) -- el frontend (Next.js) los exige en su
 * tipo `Referral`, y antes no estaban aqui. Mismo criterio de calculo
 * que AdminReferralResponse (que es la version "todos los referentes
 * a la vez" de este mismo dato) -- ver ese DTO para el detalle
 * completo de cada campo.
 */
public record ReferralResponse(
        UUID id,
        UUID referredUserId,
        String referredFullName,
        String referredEmail,
        BigDecimal bonusAmount,
        ReferralStatus status,
        long seminarsCount,
        OffsetDateTime qualifiedAt,
        OffsetDateTime bonusPaidAt
) {
}
