package com.luxtrox.backend.controller;

import com.luxtrox.backend.dto.cashback.CashbackSummaryResponse;
import com.luxtrox.backend.entity.User;
import com.luxtrox.backend.repository.CashbackTransactionRepository;
import com.luxtrox.backend.security.CustomUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/**
 * Saldo y resumen de cashback del usuario. Tras eliminar el módulo
 * Driver, el "cashback" del usuario son sus comisiones de referido y
 * créditos manuales acreditados a available_balance -- ya no hay
 * distribución mensual ni posiciones.
 */
@RestController
@RequestMapping("/cashback")
@Tag(name = "Cashback", description = "Saldo y comisiones del usuario autenticado")
public class CashbackController {

    private final CashbackTransactionRepository cashbackTransactionRepository;

    public CashbackController(CashbackTransactionRepository cashbackTransactionRepository) {
        this.cashbackTransactionRepository = cashbackTransactionRepository;
    }

    @GetMapping("/summary")
    @Transactional(readOnly = true)
    @Operation(summary = "Resumen de saldo del usuario: disponible y total acreditado")
    public CashbackSummaryResponse summary(@AuthenticationPrincipal CustomUserPrincipal principal) {
        User user = principal.getUser();

        BigDecimal available = user.getAvailableBalance();
        BigDecimal totalAccredited = cashbackTransactionRepository.sumAllForUser(user);

        return new CashbackSummaryResponse(
                totalAccredited,   // totalGenerated
                totalAccredited,   // totalReceived
                available,         // available (saldo retirable)
                BigDecimal.ZERO,   // targetFinal -- Driver eliminado
                BigDecimal.ZERO,   // progress -- Driver eliminado
                0L                 // seminarsCount -- Driver eliminado
        );
    }
}
