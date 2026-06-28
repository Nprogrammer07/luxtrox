package com.luxtrox.backend.dto.cashback;

import com.luxtrox.backend.entity.CashbackTransaction;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Una fila de CashbackTransaction (MONTHLY_PERFORMANCE o
 * MONTHLY_PERFORMANCE_REASSIGNED -- los tipos de referido quedan
 * fuera, son un dominio separado en el frontend). "status" siempre
 * vale "PAID": en este backend una transaccion solo se crea una vez
 * que el monto YA fue acreditado -- no existe un estado "pendiente"
 * o "procesando" separado, a diferencia de lo que el tipo
 * `CashbackRecord` del frontend permite.
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
        var performance = tx.getSourcePerformance();
        return new CashbackRecordResponse(
                tx.getId(),
                tx.getPosition().getUser().getId(),
                tx.getPosition().getId(),
                tx.getAmount(),
                performance != null ? performance.getMonth() : null,
                performance != null ? performance.getYear() : null,
                "PAID",
                tx.getCreatedAt(),
                tx.getCreatedAt()
        );
    }
}
