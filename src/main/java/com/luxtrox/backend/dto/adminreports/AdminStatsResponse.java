package com.luxtrox.backend.dto.adminreports;

import java.math.BigDecimal;

/**
 * Ver AdminReportsService para el mapeo completo de cada campo contra
 * las tablas reales -- varios de estos nombres vienen del frontend
 * (Next.js), que los modelo de forma especulativa antes de que este
 * backend existiera, asi que el mapeo no siempre es literal.
 */
public record AdminStatsResponse(
        long totalUsers,
        long activeUsers,
        long totalSeminars,
        BigDecimal totalCapital,
        BigDecimal totalCashbackPaid,
        long pendingWithdrawals,
        BigDecimal pendingWithdrawalsAmount,
        long totalReferrals,
        BigDecimal monthlyRevenue,
        BigDecimal monthlyGrowth
) {
}