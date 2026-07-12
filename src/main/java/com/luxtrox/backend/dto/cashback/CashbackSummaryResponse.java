package com.luxtrox.backend.dto.cashback;

import java.math.BigDecimal;

/**
 * Resumen de saldo del usuario. Tras eliminar el módulo Driver ya no
 * hay "meta final" ni progreso de cashback por posiciones: los campos
 * targetFinal, progress y seminarsCount se conservan en 0 solo porque
 * el tipo CashbackSummary del frontend (Next.js) los define.
 *
 * available       = saldo disponible para retirar (comisiones + créditos)
 * totalGenerated  = total histórico acreditado al usuario
 * totalReceived   = igual a totalGenerated (sin distinción tras Driver)
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
