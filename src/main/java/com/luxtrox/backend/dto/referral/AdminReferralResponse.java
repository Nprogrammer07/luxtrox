package com.luxtrox.backend.dto.referral;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Separado de ReferralResponse (la lista del PROPIO usuario, donde
 * "quien es el referente" es implicito) -- el admin necesita ver
 * referrerId explicito, abarca TODOS los referentes a la vez.
 *
 * status: PENDING_PURCHASE -> "active" (la relacion sigue viva, el
 * referido todavia puede comprar), RESOLVED -> "inactive" (la
 * evaluacion ya se hizo, pagada o perdida -- el detalle vive en
 * cashback_transactions/audit_logs, no en este enum). Interpretacion
 * propia: el tipo `Referral` del frontend solo admite ese binario, el
 * backend tiene un enum de 4 valores con mas matiz (ver ReferralStatus,
 * dos de ellos @Deprecated).
 *
 * bonusAmount: suma de CashbackTransaction.amount con sourceReferral
 * apuntando a esta fila -- "cuanto se le pago efectivamente por esta
 * referencia" (puede ser 0 si todavia esta PENDING_PURCHASE, o si se
 * perdio/forfeited).
 */
public record AdminReferralResponse(
        UUID id,
        UUID referrerId,
        UUID referredUserId,
        String referredUserName,
        String referredUserEmail,
        BigDecimal bonusAmount,
        String status,
        OffsetDateTime joinedAt,
        long seminarsCount
) {
}
