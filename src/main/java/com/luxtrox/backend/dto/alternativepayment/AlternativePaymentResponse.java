package com.luxtrox.backend.dto.alternativepayment;

import com.luxtrox.backend.entity.AlternativePaymentRequest;
import com.luxtrox.backend.entity.enums.AlternativePaymentStatus;
import com.luxtrox.backend.entity.enums.PlanType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Se usa tanto para el endpoint del propio usuario (consultar su
 * solicitud) como para el del admin (revisar la cola pendiente) --
 * incluir el comprador/monto/plan no es informacion sensible para el
 * propio usuario (es SU compra), y le ahorra al admin una consulta
 * aparte para tener contexto al revisar.
 */
public record AlternativePaymentResponse(
        UUID id,
        UUID purchaseId,
        PlanType planType,
        BigDecimal totalAmount,
        String buyerFullName,
        String buyerEmail,
        AlternativePaymentStatus status,
        boolean hasProofUploaded,
        OffsetDateTime expiresAt,
        OffsetDateTime reviewedAt,
        String adminNotes
) {
    public static AlternativePaymentResponse from(AlternativePaymentRequest request) {
        var purchase = request.getPurchase();
        var buyer = purchase.getUser();
        return new AlternativePaymentResponse(
                request.getId(),
                purchase.getId(),
                purchase.getPlanType(),
                purchase.getTotalAmount(),
                buyer.getFullName(),
                buyer.getEmail(),
                request.getStatus(),
                request.getPaymentProofStorageKey() != null,
                request.getExpiresAt(),
                request.getReviewedAt(),
                request.getAdminNotes()
        );
    }
}