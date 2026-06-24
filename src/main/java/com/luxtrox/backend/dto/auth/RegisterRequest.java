package com.luxtrox.backend.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "El nombre completo es obligatorio")
        String fullName,

        @NotBlank(message = "El email es obligatorio")
        @Email(message = "El email no tiene un formato valido")
        String email,

        @NotBlank(message = "El telefono es obligatorio")
        String phone,

        @NotBlank(message = "La contrasena es obligatoria")
        @Size(min = 8, message = "La contrasena debe tener al menos 8 caracteres")
        String password,

        // Opcional -- si viene, debe corresponder al referral_code de
        // un usuario existente (se valida en AuthService).
        String referralCode
) {
}
