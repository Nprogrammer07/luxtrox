package com.luxtrox.backend.dto.user;

import com.luxtrox.backend.entity.enums.UserStatus;
import jakarta.validation.constraints.NotNull;

/**
 * status va en MAYUSCULAS (ACTIVE/INACTIVE/SUSPENDED, igual que el
 * enum de UserStatus) -- la traduccion a minusculas que usa el
 * frontend internamente (Next.js) pasa por su propia capa de
 * servicio, no por este endpoint.
 */
public record UpdateUserStatusRequest(
        @NotNull(message = "El status es obligatorio")
        UserStatus status
) {
}
