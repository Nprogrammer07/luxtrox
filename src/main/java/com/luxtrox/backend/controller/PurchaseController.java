package com.luxtrox.backend.controller;

import com.luxtrox.backend.dto.plus.PlusLicenseResponse;
import com.luxtrox.backend.dto.purchase.AdminSeminarResponse;
import com.luxtrox.backend.dto.purchase.CreatePurchaseRequest;
import com.luxtrox.backend.dto.purchase.PurchaseResponse;
import com.luxtrox.backend.dto.purchase.ZenithLicenseResponse;
import com.luxtrox.backend.entity.Purchase;
import com.luxtrox.backend.entity.enums.PaymentMethod;
import com.luxtrox.backend.entity.enums.PlanType;
import com.luxtrox.backend.repository.PurchaseRepository;
import com.luxtrox.backend.security.CustomUserPrincipal;
import com.luxtrox.backend.service.AlternativePaymentService;
import com.luxtrox.backend.service.PlusService;
import com.luxtrox.backend.service.PurchaseService;
import com.luxtrox.backend.service.ZenithService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/purchases")
@Tag(name = "Purchases", description = "Compras de los planes Driver, Zenith y Plus")
public class PurchaseController {

    private final PurchaseService purchaseService;
    private final PurchaseRepository purchaseRepository;
    private final AlternativePaymentService alternativePaymentService;
    private final ZenithService zenithService;
    private final PlusService plusService;

    public PurchaseController(PurchaseService purchaseService,
                               PurchaseRepository purchaseRepository,
                               AlternativePaymentService alternativePaymentService,
                               ZenithService zenithService,
                               PlusService plusService) {
        this.purchaseService = purchaseService;
        this.purchaseRepository = purchaseRepository;
        this.alternativePaymentService = alternativePaymentService;
        this.zenithService = zenithService;
        this.plusService = plusService;
    }

    @PostMapping
    @Operation(summary = "Crear una compra (Driver, Zenith o Plus) en estado PENDING. "
            + "Si paymentMethod=CRYPTO, la respuesta incluye cryptoInvoiceUrl para redirigir al usuario a pagar. "
            + "Si paymentMethod=ALTERNATIVE, se crea automáticamente la solicitud de pago manual.")
    public ResponseEntity<PurchaseResponse> create(@AuthenticationPrincipal CustomUserPrincipal principal,
                                                    @Valid @RequestBody CreatePurchaseRequest request) {
        Purchase purchase;
        if (request.planType() == PlanType.DRIVER) {
            purchase = purchaseService.createDriverPurchase(
                    principal.getUser(), request.packageQuantity(), request.paymentMethod());
        } else if (request.planType() == PlanType.ZENITH) {
            purchase = purchaseService.createZenithPurchase(
                    principal.getUser(), request.paymentMethod());
        } else {
            // PLUS
            purchase = purchaseService.createPlusPurchase(
                    principal.getUser(), request.paymentMethod());
        }

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
    public ResponseEntity<List<PurchaseResponse>> myPurchases(
            @AuthenticationPrincipal CustomUserPrincipal principal) {
        List<PurchaseResponse> response = purchaseRepository.findByUser(principal.getUser())
                .stream().map(p -> toResponse(p, null)).toList();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/positions")
    @Operation(summary = "Listar mis seminarios Driver (InvestmentPosition confirmadas)")
    public List<AdminSeminarResponse> myPositions(
            @AuthenticationPrincipal CustomUserPrincipal principal) {
        return purchaseService.listMySeminars(principal.getUser());
    }

    @GetMapping("/zenith-licenses")
    @Operation(summary = "Listar mis licencias Zenith")
    public List<ZenithLicenseResponse> myZenithLicenses(
            @AuthenticationPrincipal CustomUserPrincipal principal) {
        return zenithService.listMyLicenses(principal.getUser());
    }

    @GetMapping("/plus-licenses")
    @Operation(summary = "Listar mis licencias Plus")
    public List<PlusLicenseResponse> myPlusLicenses(
            @AuthenticationPrincipal CustomUserPrincipal principal) {
        return plusService.getLicensesForUser(principal.getUser()).stream()
                .map(PlusLicenseResponse::from)
                .toList();
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