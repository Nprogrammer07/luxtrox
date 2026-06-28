package com.luxtrox.backend.dto.user;

import com.luxtrox.backend.entity.User;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Para GET /users/me y GET /admin/users/{id}. Varios campos del tipo
 * `User` del frontend (Next.js, definido antes de que este backend
 * existiera) no tienen equivalente real aqui -- ver UserService para
 * el mapeo completo y las simplificaciones documentadas.
 */
public record UserProfileResponse(
        UUID id,
        String name,
        String email,
        String phone,
        String role,
        String referralCode,
        String referredBy,
        long seminarsCount,
        int maxSeminars,
        BigDecimal totalInvested,
        String status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    /**
     * maxSeminars: no existe ningun tope real de paquetes Driver por
     * usuario en este negocio (ver PurchaseService) -- el frontend lo
     * exige como numero NO opcional en su tipo `User`, asi que se
     * devuelve un valor generoso fijo en vez de inventar una regla de
     * negocio que no existe. Si en algun momento se define un tope
     * real, este es el lugar para reemplazarlo.
     */
    public static final int NO_REAL_CAP_PLACEHOLDER = 9999;

    public static UserProfileResponse from(User user, long seminarsCount, BigDecimal totalInvested) {
        return new UserProfileResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getPhone(),
                user.getRole().getName(),
                user.getReferralCode(),
                user.getReferredBy() != null ? user.getReferredBy().getReferralCode() : null,
                seminarsCount,
                NO_REAL_CAP_PLACEHOLDER,
                totalInvested,
                user.getStatus().name(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}
