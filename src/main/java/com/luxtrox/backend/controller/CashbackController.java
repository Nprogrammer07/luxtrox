package com.luxtrox.backend.controller;

import com.luxtrox.backend.dto.cashback.CashbackTransactionResponse;
import com.luxtrox.backend.entity.CashbackTransaction;
import com.luxtrox.backend.entity.InvestmentPosition;
import com.luxtrox.backend.exception.ResourceNotFoundException;
import com.luxtrox.backend.repository.CashbackTransactionRepository;
import com.luxtrox.backend.repository.InvestmentPositionRepository;
import com.luxtrox.backend.security.CustomUserPrincipal;
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
 * Historial de cashback del usuario autenticado, posicion por
 * posicion -- no se expone "todas las transacciones de todos" aqui,
 * eso seria un endpoint de admin si llega a necesitarse mas adelante.
 */
@RestController
@RequestMapping("/cashback")
@Tag(name = "Cashback", description = "Historial de cashback del usuario autenticado")
public class CashbackController {

    private final InvestmentPositionRepository positionRepository;
    private final CashbackTransactionRepository transactionRepository;

    public CashbackController(InvestmentPositionRepository positionRepository,
                               CashbackTransactionRepository transactionRepository) {
        this.positionRepository = positionRepository;
        this.transactionRepository = transactionRepository;
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
