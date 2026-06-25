package com.luxtrox.backend.dto.purchase;

import com.luxtrox.backend.entity.enums.PaymentMethod;
import com.luxtrox.backend.entity.enums.PlanType;
import jakarta.validation.constraints.NotNull;

public record CreatePurchaseRequest(
        @NotNull(message = "El plan es obligatorio")
        PlanType planType,

        // Ignorado para ZENITH (siempre se trata como 1). Obligatorio
        // para DRIVER, validado en el servicio (no aqui, porque su
        // rango valido depende de cuanto ya tenga acumulado el usuario).
        Integer packageQuantity,

        @NotNull(message = "El metodo de pago es obligatorio")
        PaymentMethod paymentMethod
) {
}
