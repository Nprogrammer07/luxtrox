package com.luxtrox.backend.dto.adminreports;

import java.math.BigDecimal;

public record ChartDataPointResponse(
        String date,
        BigDecimal value
) {
}
