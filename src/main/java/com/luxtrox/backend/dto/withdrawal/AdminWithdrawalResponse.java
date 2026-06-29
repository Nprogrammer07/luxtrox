package com.luxtrox.backend.dto.withdrawal;

import com.luxtrox.backend.entity.enums.WithdrawalStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Separado de WithdrawalResponse (que usan requestCrypto/requestBank/
 * myWithdrawals) a proposito -- esos 3 endpoints son del propio
 * usuario, que ya conoce sus datos, asi que no necesitan repetirlos.
 * El admin si necesita ver quien pide el retiro y a donde se le debe
 * pagar.
 *
 * walletAddress/network quedan null para retiros BANK (y
 * accountNumber/bankName, etc. no se incluyen aqui en absoluto) --
 * el tipo `Withdrawal` del frontend (Next.js) es deliberadamente
 * solo-cripto, sin un concepto de retiro bancario todavia. Ver
 * docs/domain-model.md adenda correspondiente.
 */
public record AdminWithdrawalResponse(
        UUID id,
        UUID userId,
        String userName,
        String userEmail,
        String phone,
        String walletAddress,
        String network,
        BigDecimal amount,
        WithdrawalStatus status,
        String adminNotes,
        OffsetDateTime requestedAt,
        OffsetDateTime processedAt,
        OffsetDateTime paidAt,
        String transactionHash
) {
}
