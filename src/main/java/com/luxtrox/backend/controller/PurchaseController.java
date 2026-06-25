package com.luxtrox.backend.controller;

import com.luxtrox.backend.dto.purchase.CreatePurchaseRequest;
import com.luxtrox.backend.dto.purchase.PurchaseResponse;
import com.luxtrox.backend.entity.Purchase;
import com.luxtrox.backend.entity.enums.PlanType;
import com.luxtrox.backend.repository.PurchaseRepository;
import com.luxtrox.backend.security.CustomUserPrincipal;
import com.luxtrox.backend.service.PurchaseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/purchases")
@Tag(name = "Purchases", description = "Compras de los planes Driver y Zenith")
public class PurchaseController {

    private final PurchaseService purchaseService;
    private final PurchaseRepository purchaseRepository;

    public PurchaseController(PurchaseService purchaseService, PurchaseRepository purchaseRepository) {
        this.purchaseService = purchaseService;
        this.purchaseRepository = purchaseRepository;
    }

    @PostMapping
    @Operation(summary = "Crear una compra (Driver o Zenith) en estado PENDING")
    public ResponseEntity<PurchaseResponse> create(@AuthenticationPrincipal CustomUserPrincipal principal,
                                                     @Valid @RequestBody CreatePurchaseRequest request) {
        Purchase purchase = request.planType() == PlanType.DRIVER
                ? purchaseService.createDriverPurchase(principal.getUser(), request.packageQuantity(), request.paymentMethod())
                : purchaseService.createZenithPurchase(principal.getUser(), request.paymentMethod());

        return ResponseEntity.ok(toResponse(purchase));
    }

    @GetMapping
    @Operation(summary = "Listar mis propias compras")
    public ResponseEntity<List<PurchaseResponse>> myPurchases(@AuthenticationPrincipal CustomUserPrincipal principal) {
        List<PurchaseResponse> response = purchaseRepository.findByUser(principal.getUser())
                .stream().map(this::toResponse).toList();
        return ResponseEntity.ok(response);
    }

    private PurchaseResponse toResponse(Purchase purchase) {
        return new PurchaseResponse(
                purchase.getId(),
                purchase.getPlanType(),
                purchase.getPackageQuantity(),
                purchase.getTotalAmount(),
                purchase.getPaymentMethod(),
                purchase.getStatus(),
                purchase.getCreatedAt(),
                purchase.getConfirmedAt()
        );
    }
}
