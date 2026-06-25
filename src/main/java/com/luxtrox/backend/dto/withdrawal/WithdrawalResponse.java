package com.luxtrox.backend.dto.withdrawal;

import com.luxtrox.backend.entity.enums.WithdrawalStatus;
import com.luxtrox.backend.entity.enums.WithdrawalType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record WithdrawalResponse(
        UUID id,
        WithdrawalType type,
        BigDecimal amount,
        WithdrawalStatus status,
        OffsetDateTime requestedAt,
        OffsetDateTime processedAt,
        OffsetDateTime paidAt,
        String adminNotes
) {
}
