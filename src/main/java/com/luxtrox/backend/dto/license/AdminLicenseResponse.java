package com.luxtrox.backend.dto.license;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Una licencia (Zenith o Genius) de cualquier usuario -- para la vista
 * de renovaciones del admin.
 *
 * planType: "ZENITH" | "PLUS"  (Genius se muestra como "Genius" en el frontend)
 * renewalPrice: $250 para Zenith, $200 para Genius
 * daysUntilExpiry: negativo si ya venció
 */
public record AdminLicenseResponse(
        UUID id,
        UUID userId,
        String userName,
        String userEmail,
        String planType,
        String status,
        OffsetDateTime activatedAt,
        OffsetDateTime currentPeriodEnd,
        long daysUntilExpiry,
        java.math.BigDecimal renewalPrice
) {
}
