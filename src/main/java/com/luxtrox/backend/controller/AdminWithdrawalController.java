package com.luxtrox.backend.controller;

import com.luxtrox.backend.dto.withdrawal.AdminWithdrawalResponse;
import com.luxtrox.backend.dto.withdrawal.RejectWithdrawalRequest;
import com.luxtrox.backend.dto.withdrawal.WithdrawalResponse;
import com.luxtrox.backend.entity.WithdrawalRequest;
import com.luxtrox.backend.entity.enums.WithdrawalStatus;
import com.luxtrox.backend.security.CustomUserPrincipal;
import com.luxtrox.backend.service.WithdrawalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Vive bajo /admin/** literalmente -- ver la nota de
 * AdminPurchaseController sobre por que esto importa para que
 * SecurityConfig proteja estas rutas de verdad.
 */
@RestController
@RequestMapping("/admin/withdrawals")
@Tag(name = "Admin - Withdrawals", description = "Listar, aprobar, rechazar y marcar pagados los retiros")
public class AdminWithdrawalController {

    private final WithdrawalService withdrawalService;

    public AdminWithdrawalController(WithdrawalService withdrawalService) {
        this.withdrawalService = withdrawalService;
    }

    @GetMapping
    @Operation(summary = "Listar todas las solicitudes de retiro -- status es un filtro opcional")
    public List<AdminWithdrawalResponse> list(@RequestParam(required = false) WithdrawalStatus status) {
        return withdrawalService.listAll(status);
    }

    @PostMapping("/{withdrawalId}/approve")
    @Operation(summary = "Aprobar una solicitud de retiro en estado REQUESTED")
    public ResponseEntity<WithdrawalResponse> approve(@AuthenticationPrincipal CustomUserPrincipal principal,
                                                        @PathVariable UUID withdrawalId) {
        return ResponseEntity.ok(toResponse(withdrawalService.approve(withdrawalId, principal.getUser())));
    }

    /**
     * Rechazar SI devuelve el saldo al usuario (ver
     * docs/domain-model.md 4.3) -- es la unica forma de "cancelar"
     * un retiro, y solo el admin puede hacerlo.
     */
    @PostMapping("/{withdrawalId}/reject")
    @Operation(summary = "Rechazar una solicitud y devolver el saldo al usuario")
    public ResponseEntity<WithdrawalResponse> reject(@AuthenticationPrincipal CustomUserPrincipal principal,
                                                        @PathVariable UUID withdrawalId,
                                                        @RequestBody(required = false) RejectWithdrawalRequest body) {
        String notes = body != null ? body.adminNotes() : null;
        return ResponseEntity.ok(toResponse(withdrawalService.reject(withdrawalId, principal.getUser(), notes)));
    }

    @PostMapping("/{withdrawalId}/mark-paid")
    @Operation(summary = "Marcar como pagado un retiro ya aprobado")
    public ResponseEntity<WithdrawalResponse> markPaid(@AuthenticationPrincipal CustomUserPrincipal principal,
                                                          @PathVariable UUID withdrawalId) {
        return ResponseEntity.ok(toResponse(withdrawalService.markPaid(withdrawalId, principal.getUser())));
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
