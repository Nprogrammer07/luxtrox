package com.luxtrox.backend.dto.user;

import jakarta.validation.constraints.NotBlank;

/**
 * Deliberadamente NO incluye email ni password -- cambiar el email
 * necesitaria su propio flujo de re-verificacion (no construido), y
 * cambiar la contrasena necesita su propio flujo seguro separado (no
 * el mismo endpoint que actualiza nombre/telefono).
 */
public record UpdateProfileRequest(
        @NotBlank(message = "El nombre completo es obligatorio")
        String fullName,

        @NotBlank(message = "El telefono es obligatorio")
        String phone
) {
}
