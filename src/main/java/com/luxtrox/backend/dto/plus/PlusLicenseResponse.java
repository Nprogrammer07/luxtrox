package com.luxtrox.backend.dto.plus;

import com.luxtrox.backend.entity.PlusLicense;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PlusLicenseResponse(
        UUID id,
        UUID userId,
        String status,
        OffsetDateTime purchasedAt,
        OffsetDateTime expiresAt,
        OffsetDateTime createdAt
) {
    public static PlusLicenseResponse from(PlusLicense license) {
        return new PlusLicenseResponse(
                license.getId(),
                license.getUser().getId(),
                license.getStatus().name().toLowerCase(),
                license.getPurchasedAt(),
                license.getExpiresAt(),
                license.getCreatedAt()
        );
    }
}
