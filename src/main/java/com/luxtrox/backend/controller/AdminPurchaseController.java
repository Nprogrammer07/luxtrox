package com.luxtrox.backend.controller;

import com.luxtrox.backend.dto.purchase.AdminPurchaseResponse;
import com.luxtrox.backend.dto.purchase.PurchaseResponse;
import com.luxtrox.backend.entity.Purchase;
import com.luxtrox.backend.entity.enums.PurchaseStatus;
import com.luxtrox.backend.service.PurchaseService;
import com.luxtrox.backend.service.ZenithService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Acciones de administrador sobre compras y licencias Zenith.
 * Vive bajo /admin/** porque SecurityConfig protege por PREFIJO de ruta.
 */
@RestController
@RequestMapping("/admin/purchases")
@Tag(name = "Admin - Purchases", description = "Listado, aprobacion/rechazo de compras, y renovacion de Zenith")
public class AdminPurchaseController {

    private final PurchaseService purchaseService;
    private final ZenithService zenithService;

    public AdminPurchaseController(PurchaseService purchaseService, ZenithService zenithService) {
        this.purchaseService = purchaseService;
        this.zenithService = zenithService;
    }

    @GetMapping("/requests")
    @Operation(summary = "Listar todas las compras (cualquier plan, cualquier status) para aprobar/rechazar")
    public List<AdminPurchaseResponse> listPurchaseRequests(@RequestParam(required = false) PurchaseStatus status) {
        return purchaseService.listAllPurchases(status);
    }

    @PostMapping("/{purchaseId}/confirm")
    @Operation(summary = "Confirmar una compra ya pagada (crea licencia Zenith o Plus segun el plan). "
            + "Solo deberia usarse para compras ALTERNATIVE -- las CRYPTO se confirman solas via webhook.")
    public ResponseEntity<PurchaseResponse> confirm(@PathVariable UUID purchaseId) {
        Purchase purchase = purchaseService.confirmPurchase(purchaseId);
        return ResponseEntity.ok(toResponse(purchase));
    }

    @PostMapping("/{purchaseId}/reject")
    @Operation(summary = "Rechazar una compra PENDING -- no reversible, no aplica a compras ya CONFIRMED")
    public ResponseEntity<PurchaseResponse> reject(@PathVariable UUID purchaseId) {
        Purchase purchase = purchaseService.rejectPurchase(purchaseId);
        return ResponseEntity.ok(toResponse(purchase));
    }

    @PostMapping("/zenith-licenses/{licenseId}/renew")
    @Operation(summary = "Registrar el pago de renovacion anual de una licencia Zenith ($250 fijos)")
    public ResponseEntity<Void> renewZenithLicense(@PathVariable UUID licenseId) {
        zenithService.renew(licenseId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/zenith-licenses/expire-overdue")
    @Operation(summary = "Job: marcar como EXPIRED las licencias Zenith vencidas sin renovar")
    public ResponseEntity<Integer> expireOverdueZenithLicenses() {
        return ResponseEntity.ok(zenithService.expireOverdueLicenses());
    }

    private PurchaseResponse toResponse(Purchase purchase) {
        return new PurchaseResponse(
                purchase.getId(), purchase.getPlanType(), purchase.getPackageQuantity(),
                purchase.getTotalAmount(), purchase.getPaymentMethod(), purchase.getStatus(),
                purchase.getCreatedAt(), purchase.getConfirmedAt(), null
        );
    }
}
