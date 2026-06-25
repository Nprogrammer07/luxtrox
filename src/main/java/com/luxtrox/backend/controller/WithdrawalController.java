package com.luxtrox.backend.controller;

import com.luxtrox.backend.dto.withdrawal.BankWithdrawalRequest;
import com.luxtrox.backend.dto.withdrawal.CryptoWithdrawalRequest;
import com.luxtrox.backend.dto.withdrawal.WithdrawalResponse;
import com.luxtrox.backend.entity.WithdrawalRequest;
import com.luxtrox.backend.repository.WithdrawalRequestRepository;
import com.luxtrox.backend.security.CustomUserPrincipal;
import com.luxtrox.backend.service.WithdrawalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/withdrawals")
@Tag(name = "Withdrawals", description = "Solicitudes de retiro del usuario autenticado")
public class WithdrawalController {

    private final WithdrawalService withdrawalService;
    private final WithdrawalRequestRepository withdrawalRequestRepository;

    public WithdrawalController(WithdrawalService withdrawalService,
                                 WithdrawalRequestRepository withdrawalRequestRepository) {
        this.withdrawalService = withdrawalService;
        this.withdrawalRequestRepository = withdrawalRequestRepository;
    }

    @PostMapping("/crypto")
    @Operation(summary = "Solicitar un retiro via criptomoneda (minimo $50)")
    public ResponseEntity<WithdrawalResponse> requestCrypto(@AuthenticationPrincipal CustomUserPrincipal principal,
                                                              @Valid @RequestBody CryptoWithdrawalRequest request) {
        WithdrawalRequest result = withdrawalService.requestCrypto(
                principal.getUser(), request.amount(), request.fullName(), request.email(),
                request.phone(), request.blockchainNetwork(), request.walletAddress());
        return ResponseEntity.ok(toResponse(result));
    }

    @PostMapping("/bank")
    @Operation(summary = "Solicitar un retiro via transferencia bancaria (minimo $50)")
    public ResponseEntity<WithdrawalResponse> requestBank(@AuthenticationPrincipal CustomUserPrincipal principal,
                                                            @Valid @RequestBody BankWithdrawalRequest request) {
        WithdrawalRequest result = withdrawalService.requestBank(
                principal.getUser(), request.amount(), request.fullName(), request.email(), request.phone(),
                request.country(), request.bankName(), request.accountType(), request.accountNumber(),
                request.accountHolderName(), request.documentId());
        return ResponseEntity.ok(toResponse(result));
    }

    @GetMapping
    @Operation(summary = "Listar mis propias solicitudes de retiro")
    public ResponseEntity<List<WithdrawalResponse>> myWithdrawals(
            @AuthenticationPrincipal CustomUserPrincipal principal) {
        List<WithdrawalResponse> response = withdrawalRequestRepository.findByUser(principal.getUser())
                .stream().map(this::toResponse).toList();
        return ResponseEntity.ok(response);
    }

    private WithdrawalResponse toResponse(WithdrawalRequest request) {
        return new WithdrawalResponse(
                request.getId(),
                request.getType(),
                request.getAmount(),
                request.getStatus(),
                request.getRequestedAt(),
                request.getProcessedAt(),
                request.getPaidAt(),
                request.getAdminNotes()
        );
    }
}
