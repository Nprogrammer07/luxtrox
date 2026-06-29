package com.luxtrox.backend.dto.purchase;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * "Seminario" en el frontend (Next.js) = una InvestmentPosition aqui
 * -- un paquete Driver ya confirmado. Zenith no aparece en este
 * listado: genera ZenithLicense, no participa del motor de cashback
 * (ver PurchaseService), asi que no es un "seminario" en ese sentido
 * (mismo criterio usado en AdminReportsService y UserService).
 *
 * status solo puede ser "active"/"completed" (PositionStatus) -- los
 * valores "pending"/"cancelled" que el tipo `Seminar` del frontend
 * tambien admite no tienen equivalente aqui: una posicion solo se
 * crea ya CONFIRMADA, nunca existe en un estado "pendiente".
 *
 * InvestmentPosition no tiene un campo "updatedAt" real (no hay
 * seguimiento de "ultima modificacion") -- se devuelve createdAt en
 * su lugar para satisfacer el campo no-opcional del tipo `Seminar`
 * del frontend. completedAt si es un campo real, y mapea
 * directamente a `completionDate` (opcional) del frontend.
 */
public record AdminSeminarResponse(
        UUID id,
        UUID userId,
        BigDecimal capital,
        BigDecimal targetCashback,
        BigDecimal cashbackPaid,
        String status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime completionDate
) {
}
