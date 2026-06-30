package com.luxtrox.backend.dto.cashback;

import com.luxtrox.backend.entity.CashbackTransaction;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Una fila de CashbackTransaction -- cubre TODOS los tipos que
 * representan dinero realmente acreditado al usuario:
 * MONTHLY_PERFORMANCE, MONTHLY_PERFORMANCE_REASSIGNED,
 * REFERRAL_BONUS, REFERRAL_BONUS_DIRECT, MANUAL_CREDIT.
 *
 * seminarId es null para los tipos sin posicion (REFERRAL_BONUS_DIRECT
 * y MANUAL_CREDIT van directo a balance, sin posicion asociada).
 * month/year es null para tipos sin sourcePerformance.
 *
 * "status" siempre vale "PAID": en este backend una transaccion solo
 * se crea una vez que el monto YA fue acreditado.
 */
public record CashbackRecordResponse(
        UUID id,
        UUID userId,
        UUID seminarId,
        BigDecimal amount,
        Integer month,
        Integer year,
        String status,
        OffsetDateTime paidAt,
        OffsetDateTime createdAt
) {
    public static CashbackRecordResponse from(CashbackTransaction tx) {
        var position = tx.getPosition();
        var performance = tx.getSourcePerformance();
        UUID userId = position != null
                ? position.getUser().getId()
                : (tx.getUser() != null ? tx.getUser().getId() : null);
        UUID seminarId = position != null ? position.getId() : null;
        return new CashbackRecordResponse(
                tx.getId(),
                userId,
                seminarId,
                tx.getAmount(),
                performance != null ? performance.getMonth() : null,
                performance != null ? performance.getYear() : null,
                "PAID",
                tx.getCreatedAt(),
                tx.getCreatedAt()
        );
    }
}
