package com.luxtrox.backend.dto.auth;

import jakarta.validation.constraints.NotBlank;

public record RefreshTokenRequest(
        @NotBlank(message = "El refresh token es obligatorio")
        String refreshToken
) {
}
