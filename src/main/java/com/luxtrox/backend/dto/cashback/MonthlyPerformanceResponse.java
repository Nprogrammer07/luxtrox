package com.luxtrox.backend.dto.cashback;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record MonthlyPerformanceResponse(
        UUID id,
        Integer month,
        Integer year,
        BigDecimal percentage,
        OffsetDateTime appliedAt,
        OffsetDateTime createdAt
) {
}
