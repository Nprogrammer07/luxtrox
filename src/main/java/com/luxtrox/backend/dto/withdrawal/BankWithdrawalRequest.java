package com.luxtrox.backend.dto.withdrawal;

import com.luxtrox.backend.entity.enums.BankAccountType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record BankWithdrawalRequest(
        @NotNull @DecimalMin(value = "50.00", message = "El monto minimo de retiro es $50")
        BigDecimal amount,

        @NotBlank String fullName,
        @NotBlank String email,
        @NotBlank String phone,
        @NotBlank String country,
        @NotBlank String bankName,
        @NotNull BankAccountType accountType,
        @NotBlank String accountNumber,
        @NotBlank String accountHolderName,
        @NotBlank String documentId
) {
}
