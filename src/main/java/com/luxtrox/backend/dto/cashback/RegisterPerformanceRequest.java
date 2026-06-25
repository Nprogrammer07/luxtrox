package com.luxtrox.backend.dto.cashback;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record RegisterPerformanceRequest(
        @NotNull @Min(1) @Max(12)
        Integer month,

        @NotNull @Min(2024)
        Integer year,

        @NotNull @DecimalMin(value = "0.01", message = "El porcentaje debe ser mayor a 0")
        BigDecimal percentage
) {
}
