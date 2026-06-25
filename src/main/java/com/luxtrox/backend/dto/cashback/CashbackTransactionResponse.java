package com.luxtrox.backend.dto.cashback;

import com.luxtrox.backend.entity.enums.CashbackTransactionType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record CashbackTransactionResponse(
        UUID id,
        UUID positionId,
        CashbackTransactionType type,
        BigDecimal amount,
        BigDecimal effectiveRate,
        OffsetDateTime createdAt
) {
}
