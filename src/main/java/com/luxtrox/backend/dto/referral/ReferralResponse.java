package com.luxtrox.backend.dto.referral;

import com.luxtrox.backend.entity.enums.ReferralStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ReferralResponse(
        UUID id,
        String referredFullName,
        String referredEmail,
        ReferralStatus status,
        OffsetDateTime qualifiedAt,
        OffsetDateTime bonusPaidAt
) {
}
