package com.luxtrox.backend.dto.purchase;

import com.luxtrox.backend.entity.ZenithLicense;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Para GET /purchases/zenith-licenses -- "mis licencias Zenith". No
 * existia ningun endpoint que expusiera esto al usuario (ni siquiera
 * a admin) -- el frontend (Next.js) nunca tuvo ningun concepto de
 * Zenith en absoluto hasta esta integracion, solo conocia "seminario"
 * (= InvestmentPosition, Driver).
 *
 * status en MAYUSCULAS (ACTIVE/EXPIRED, igual que ZenithLicenseStatus)
 * -- la traduccion a minusculas que usa el frontend internamente pasa
 * por su propia capa de servicio (investment.service.ts), no por este
 * endpoint (mismo criterio ya usado para role/status de User).
 */
public record ZenithLicenseResponse(
        UUID id,
        String status,
        OffsetDateTime activatedAt,
        OffsetDateTime currentPeriodEnd,
        OffsetDateTime createdAt
) {
    public static ZenithLicenseResponse from(ZenithLicense license) {
        return new ZenithLicenseResponse(
                license.getId(),
                license.getStatus().name(),
                license.getActivatedAt(),
                license.getCurrentPeriodEnd(),
                license.getCreatedAt()
        );
    }
}
