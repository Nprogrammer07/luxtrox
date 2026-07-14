package com.luxtrox.backend.dto.plus;

import com.luxtrox.backend.entity.PlusLicense;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * currentPeriodEnd = hasta cuándo tiene acceso a los recursos académicos.
 * Se extiende 1 año en cada renovación ($200/año).
 */
public record PlusLicenseResponse(
        UUID id,
        UUID userId,
        String status,
        OffsetDateTime purchasedAt,
        OffsetDateTime currentPeriodEnd,
        OffsetDateTime createdAt
) {
    public static PlusLicenseResponse from(PlusLicense license) {
        return new PlusLicenseResponse(
                license.getId(),
                license.getUser().getId(),
                license.getStatus().name().toLowerCase(),
                license.getPurchasedAt(),
                license.getCurrentPeriodEnd(),
                license.getCreatedAt()
        );
    }
}
