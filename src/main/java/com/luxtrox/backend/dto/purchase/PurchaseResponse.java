package com.luxtrox.backend.dto.purchase;

import com.luxtrox.backend.entity.enums.PaymentMethod;
import com.luxtrox.backend.entity.enums.PlanType;
import com.luxtrox.backend.entity.enums.PurchaseStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PurchaseResponse(
        UUID id,
        PlanType planType,
        Integer packageQuantity,
        BigDecimal totalAmount,
        PaymentMethod paymentMethod,
        PurchaseStatus status,
        OffsetDateTime createdAt,
        OffsetDateTime confirmedAt
) {
}
