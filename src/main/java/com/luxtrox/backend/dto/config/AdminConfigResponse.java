package com.luxtrox.backend.dto.config;

import java.math.BigDecimal;

/**
 * cashbackRatePct se expone como porcentaje legible (300.00 = 300%)
 * para que el panel de admin pueda mostrar el valor humano directamente.
 */
public record AdminConfigResponse(
        BigDecimal driverPrice,
        int maxDriverPositions,
        BigDecimal cashbackRatePct,
        BigDecimal minWithdrawal
) {
}
