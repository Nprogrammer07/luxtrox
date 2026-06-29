package com.luxtrox.backend.controller;

import com.luxtrox.backend.dto.purchase.AdminSeminarResponse;
import com.luxtrox.backend.dto.purchase.PurchaseResponse;
import com.luxtrox.backend.entity.Purchase;
import com.luxtrox.backend.service.PurchaseService;
import com.luxtrox.backend.service.ZenithService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Acciones de administrador sobre compras y licencias Zenith. Vive
 * deliberadamente bajo /admin/** (no /purchases/admin/...) porque
 * SecurityConfig protege por PREFIJO de ruta -- solo lo que empieza
 * literalmente con /admin/ exige hasRole("ADMIN").
 */
@RestController
@RequestMapping("/admin/purchases")
@Tag(name = "Admin - Purchases", description = "Listado, confirmacion de compras, y renovacion de Zenith")
public class AdminPurchaseController {

    private final PurchaseService purchaseService;
    private final ZenithService zenithService;

    public AdminPurchaseController(PurchaseService purchaseService, ZenithService zenithService) {
        this.purchaseService = purchaseService;
        this.zenithService = zenithService;
    }

    @GetMapping
    @Operation(summary = "Listar todos los 'seminarios' (posiciones Driver confirmadas), de todos los usuarios")
    public List<AdminSeminarResponse> listSeminars() {
        return purchaseService.listAllSeminars();
    }

    @PostMapping("/{purchaseId}/confirm")
    @Operation(summary = "Confirmar una compra ya pagada (crea posicion o licencia segun el plan). "
            + "Solo deberia usarse para compras ALTERNATIVE -- las CRYPTO se confirman solas via webhook.")
    public ResponseEntity<PurchaseResponse> confirm(@PathVariable UUID purchaseId) {
        Purchase purchase = purchaseService.confirmPurchase(purchaseId);
        return ResponseEntity.ok(new PurchaseResponse(
                purchase.getId(), purchase.getPlanType(), purchase.getPackageQuantity(),
                purchase.getTotalAmount(), purchase.getPaymentMethod(), purchase.getStatus(),
                purchase.getCreatedAt(), purchase.getConfirmedAt(), null
        ));
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
}
