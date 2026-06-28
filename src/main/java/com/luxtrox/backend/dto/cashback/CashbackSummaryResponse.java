package com.luxtrox.backend.dto.cashback;

import java.math.BigDecimal;

/**
 * Ver CashbackQueryService para el mapeo completo. Nota: totalGenerated
 * y totalReceived son el MISMO valor en este backend -- el motor de
 * cashback credita el saldo de forma inmediata al distribuir, no
 * existe un estado intermedio "generado pero no recibido todavia".
 * Se exponen como dos campos separados solo porque asi los definio el
 * frontend (Next.js) de forma especulativa antes de que este backend
 * existiera.
 */
public record CashbackSummaryResponse(
        BigDecimal totalGenerated,
        BigDecimal totalReceived,
        BigDecimal available,
        BigDecimal targetFinal,
        BigDecimal progress,
        long seminarsCount
) {
}
