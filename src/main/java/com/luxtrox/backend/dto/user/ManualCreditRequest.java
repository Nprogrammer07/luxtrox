package com.luxtrox.backend.dto.user;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * Para POST /admin/users/{userId}/credit -- el admin envia dinero
 * manualmente a un usuario. reason distingue si es por rendimiento
 * (PERFORMANCE) o por comision de venta (COMMISSION) -- ambos van al
 * available_balance del usuario via CashbackTransaction.MANUAL_CREDIT.
 */
public record ManualCreditRequest(
        @NotNull(message = "El monto es obligatorio")
        @DecimalMin(value = "0.01", message = "El monto minimo es $0.01")
        BigDecimal amount,

        @NotNull(message = "El motivo es obligatorio")
        CreditReason reason,

        @NotBlank(message = "Las notas son obligatorias")
        String notes
) {
    public enum CreditReason {
        PERFORMANCE,   // rendimiento mensual u otro pago de rendimiento
        COMMISSION     // comision de venta fuera del flujo automatico
    }
}
