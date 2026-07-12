package com.luxtrox.backend.dto.config;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record AdminConfigUpdateRequest(
        @NotNull @DecimalMin("1.00") BigDecimal minWithdrawal
) {}
