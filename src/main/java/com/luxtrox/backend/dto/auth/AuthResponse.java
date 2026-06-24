package com.luxtrox.backend.dto.auth;

import java.util.UUID;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresInMs,
        UserSummary user
) {
    public record UserSummary(
            UUID id,
            String fullName,
            String email,
            String role,
            String referralCode
    ) {
    }
}
