package com.luxtrox.backend.controller;

import com.luxtrox.backend.dto.cashback.CashbackMonthlyResponse;
import com.luxtrox.backend.dto.cashback.CashbackRecordResponse;
import com.luxtrox.backend.dto.cashback.CashbackSummaryResponse;
import com.luxtrox.backend.dto.cashback.CashbackTransactionResponse;
import com.luxtrox.backend.entity.CashbackTransaction;
import com.luxtrox.backend.entity.InvestmentPosition;
import com.luxtrox.backend.exception.ResourceNotFoundException;
import com.luxtrox.backend.repository.CashbackTransactionRepository;
import com.luxtrox.backend.repository.InvestmentPositionRepository;
import com.luxtrox.backend.security.CustomUserPrincipal;
import com.luxtrox.backend.service.CashbackQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Historial de cashback del usuario autenticado -- no se expone
 * "todas las transacciones de todos" aqui, eso es AdminCashbackController.
 */
@RestController
@RequestMapping("/cashback")
@Tag(name = "Cashback", description = "Historial de cashback del usuario autenticado")
public class CashbackController {

    private final InvestmentPositionRepository positionRepository;
    private final CashbackTransactionRepository transactionRepository;
    private final CashbackQueryService cashbackQueryService;

    public CashbackController(InvestmentPositionRepository positionRepository,
                               CashbackTransactionRepository transactionRepository,
                               CashbackQueryService cashbackQueryService) {
        this.positionRepository = positionRepository;
        this.transactionRepository = transactionRepository;
        this.cashbackQueryService = cashbackQueryService;
    }

    @GetMapping("/summary")
    @Operation(summary = "Resumen de cashback (totales, saldo disponible, progreso hacia la meta)")
    public CashbackSummaryResponse summary(@AuthenticationPrincipal CustomUserPrincipal principal) {
        return cashbackQueryService.getSummary(principal.getUser());
    }

    @GetMapping("/history")
    @Operation(summary = "Historial de cashback recibido (todas mis posiciones Driver)")
    public List<CashbackRecordResponse> history(@AuthenticationPrincipal CustomUserPrincipal principal) {
        return cashbackQueryService.getHistory(principal.getUser());
    }

    @GetMapping("/monthly")
    @Operation(summary = "Cashback recibido por mes, ultimos 6 meses (para la grafica)")
    public List<CashbackMonthlyResponse> monthly(@AuthenticationPrincipal CustomUserPrincipal principal) {
        return cashbackQueryService.getMonthlyChart(principal.getUser());
    }

    @GetMapping("/positions/{positionId}/transactions")
    @Operation(summary = "Historial de cashback de una de mis posiciones")
    public ResponseEntity<List<CashbackTransactionResponse>> transactionsForPosition(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable UUID positionId) {

        InvestmentPosition position = positionRepository.findById(positionId)
                .filter(p -> p.getUser().getId().equals(principal.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Posicion no encontrada"));

        List<CashbackTransactionResponse> response = transactionRepository.findByPosition(position)
                .stream()
                .map(this::toResponse)
                .toList();

        return ResponseEntity.ok(response);
    }

    private CashbackTransactionResponse toResponse(CashbackTransaction tx) {
        return new CashbackTransactionResponse(
                tx.getId(),
                tx.getPosition() != null ? tx.getPosition().getId() : null,
                tx.getType(),
                tx.getAmount(),
                tx.getEffectiveRate(),
                tx.getCreatedAt()
        );
    }
}
