package com.luxtrox.backend.dto.config;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record AdminConfigUpdateRequest(

        @NotNull
        @DecimalMin(value = "1.00", message = "El precio del plan Driver debe ser al menos $1")
        BigDecimal driverPrice,

        @NotNull
        @Min(value = 1, message = "El máximo de posiciones debe ser al menos 1")
        Integer maxDriverPositions,

        @NotNull
        @DecimalMin(value = "1.00", message = "La tasa de cashback debe ser al menos 1%")
        BigDecimal cashbackRatePct,

        @NotNull
        @DecimalMin(value = "1.00", message = "El retiro mínimo debe ser al menos $1")
        BigDecimal minWithdrawal
) {
}
