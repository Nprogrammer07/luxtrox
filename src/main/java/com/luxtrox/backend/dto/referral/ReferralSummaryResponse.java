package com.luxtrox.backend.dto.referral;

import java.math.BigDecimal;

/**
 * pendingBonus siempre vale 0 en este backend a proposito: la
 * evaluacion de una comision de referido es UNICA, en el momento
 * exacto en que se confirma la compra del referido -- sin
 * reintentos, sin espera (ver docs/domain-model.md adenda de Fase 8).
 * No existe un estado "pendiente" intermedio: o se paga de inmediato,
 * o se pierde para siempre en ese mismo instante. El campo se
 * conserva solo porque el tipo `ReferralSummary` del frontend
 * (Next.js) lo definio de forma especulativa antes de que este
 * backend existiera.
 */
public record ReferralSummaryResponse(
        String code,
        long totalReferrals,
        long activeReferrals,
        BigDecimal totalBonusEarned,
        BigDecimal pendingBonus
) {
}
