package com.luxtrox.backend.dto.purchase;

import com.luxtrox.backend.entity.enums.PaymentMethod;
import com.luxtrox.backend.entity.enums.PlanType;
import com.luxtrox.backend.entity.enums.PurchaseStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Para el flujo de aprobar/rechazar compras del admin -- distinto de
 * AdminSeminarResponse (que representa InvestmentPosition, solo
 * compras Driver YA confirmadas). Esta es la compra en si, en
 * CUALQUIER estado (PENDING incluido, que es lo que el admin
 * realmente necesita revisar -- AdminSeminarResponse no puede
 * mostrar nada PENDING porque la posicion/licencia ni siquiera
 * existe todavia en ese estado).
 */
public record AdminPurchaseResponse(
        UUID id,
        UUID userId,
        String userName,
        String userEmail,
        PlanType planType,
        Integer packageQuantity,
        BigDecimal totalAmount,
        PaymentMethod paymentMethod,
        PurchaseStatus status,
        OffsetDateTime createdAt,
        OffsetDateTime confirmedAt
) {
}
