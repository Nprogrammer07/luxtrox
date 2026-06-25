package com.luxtrox.backend.dto.withdrawal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CryptoWithdrawalRequest(
        @NotNull @DecimalMin(value = "50.00", message = "El monto minimo de retiro es $50")
        BigDecimal amount,

        @NotBlank String fullName,
        @NotBlank String email,
        @NotBlank String phone,
        @NotBlank String blockchainNetwork,
        @NotBlank String walletAddress
) {
}
