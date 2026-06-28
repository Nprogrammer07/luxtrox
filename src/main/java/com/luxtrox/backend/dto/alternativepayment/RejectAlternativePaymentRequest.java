package com.luxtrox.backend.dto.alternativepayment;

import jakarta.validation.constraints.NotBlank;

public record RejectAlternativePaymentRequest(
        @NotBlank(message = "Las notas del rechazo son obligatorias")
        String adminNotes
) {
}
