package com.luxtrox.backend.dto.referral;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Separado de ReferralResponse (la lista del PROPIO usuario, donde
 * "quien es el referente" es implicito) -- el admin necesita ver
 * referrerId explicito, abarca TODOS los referentes a la vez.
 *
 * NOTA: los nombres referredFullName / referredEmail coinciden
 * deliberadamente con ReferralResponse (endpoint del usuario) para
 * que el frontend pueda reutilizar el mismo mapper (mapBackendReferral
 * en referral.service.ts) sin distincion entre los dos endpoints.
 */
public record AdminReferralResponse(
        UUID id,
        UUID referrerId,
        UUID referredUserId,
        String referredFullName,
        String referredEmail,
        BigDecimal bonusAmount,
        String status,
        OffsetDateTime joinedAt,
        long seminarsCount
) {
}
