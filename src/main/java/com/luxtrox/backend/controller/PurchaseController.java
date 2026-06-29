package com.luxtrox.backend.controller;

import com.luxtrox.backend.dto.purchase.AdminSeminarResponse;
import com.luxtrox.backend.dto.purchase.CreatePurchaseRequest;
import com.luxtrox.backend.dto.purchase.PurchaseResponse;
import com.luxtrox.backend.entity.Purchase;
import com.luxtrox.backend.entity.enums.PaymentMethod;
import com.luxtrox.backend.entity.enums.PlanType;
import com.luxtrox.backend.repository.PurchaseRepository;
import com.luxtrox.backend.security.CustomUserPrincipal;
import com.luxtrox.backend.service.AlternativePaymentService;
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
    private final AlternativePaymentService alternativePaymentService;

    public PurchaseController(PurchaseService purchaseService, PurchaseRepository purchaseRepository,
                               AlternativePaymentService alternativePaymentService) {
        this.purchaseService = purchaseService;
        this.purchaseRepository = purchaseRepository;
        this.alternativePaymentService = alternativePaymentService;
    }

    @PostMapping
    @Operation(summary = "Crear una compra (Driver o Zenith) en estado PENDING. "
            + "Si paymentMethod=CRYPTO, la respuesta incluye cryptoInvoiceUrl para redirigir al usuario a pagar. "
            + "Si paymentMethod=ALTERNATIVE, se crea automaticamente la solicitud de pago manual "
            + "(ver POST /alternative-payments/{id}/proof para subir el comprobante una vez aprobada).")
    public ResponseEntity<PurchaseResponse> create(@AuthenticationPrincipal CustomUserPrincipal principal,
                                                     @Valid @RequestBody CreatePurchaseRequest request) {
        Purchase purchase = request.planType() == PlanType.DRIVER
                ? purchaseService.createDriverPurchase(principal.getUser(), request.packageQuantity(), request.paymentMethod())
                : purchaseService.createZenithPurchase(principal.getUser(), request.paymentMethod());

        String cryptoInvoiceUrl = null;
        if (purchase.getPaymentMethod() == PaymentMethod.CRYPTO) {
            cryptoInvoiceUrl = purchaseService.initiateCryptoPayment(purchase);
        } else if (purchase.getPaymentMethod() == PaymentMethod.ALTERNATIVE) {
            alternativePaymentService.createRequest(purchase);
        }

        return ResponseEntity.ok(toResponse(purchase, cryptoInvoiceUrl));
    }

    @GetMapping
    @Operation(summary = "Listar mis propias compras")
    public ResponseEntity<List<PurchaseResponse>> myPurchases(@AuthenticationPrincipal CustomUserPrincipal principal) {
        List<PurchaseResponse> response = purchaseRepository.findByUser(principal.getUser())
                .stream().map(p -> toResponse(p, null)).toList();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/positions")
    @Operation(summary = "Listar mis 'seminarios' (InvestmentPosition ya confirmadas) -- "
            + "distinto de GET /purchases, que devuelve compras (incluye PENDING, sin capital/cashback)")
    public List<AdminSeminarResponse> myPositions(@AuthenticationPrincipal CustomUserPrincipal principal) {
        return purchaseService.listMySeminars(principal.getUser());
    }

    private PurchaseResponse toResponse(Purchase purchase, String cryptoInvoiceUrl) {
        return new PurchaseResponse(
                purchase.getId(),
                purchase.getPlanType(),
                purchase.getPackageQuantity(),
                purchase.getTotalAmount(),
                purchase.getPaymentMethod(),
                purchase.getStatus(),
                purchase.getCreatedAt(),
                purchase.getConfirmedAt(),
                cryptoInvoiceUrl
        );
    }
}