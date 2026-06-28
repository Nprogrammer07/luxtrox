package com.luxtrox.backend.controller;

import com.luxtrox.backend.dto.alternativepayment.AlternativePaymentResponse;
import com.luxtrox.backend.entity.AlternativePaymentRequest;
import com.luxtrox.backend.entity.Purchase;
import com.luxtrox.backend.entity.User;
import com.luxtrox.backend.exception.BusinessRuleException;
import com.luxtrox.backend.exception.ResourceNotFoundException;
import com.luxtrox.backend.repository.AlternativePaymentRequestRepository;
import com.luxtrox.backend.repository.PurchaseRepository;
import com.luxtrox.backend.security.CustomUserPrincipal;
import com.luxtrox.backend.service.AlternativePaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

/**
 * Lado del USUARIO del flujo de pago manual (ver
 * docs/domain-model.md S3.5 y AlternativePaymentService para el
 * detalle completo de la maquina de estados). El lado del admin esta
 * en AdminAlternativePaymentController.
 */
@RestController
@RequestMapping("/purchases/{purchaseId}/alternative-payment")
@Tag(name = "Alternative Payments", description = "Flujo manual de pago (no-cripto) -- lado del usuario")
public class AlternativePaymentController {

    private static final long MAX_PROOF_SIZE_BYTES = 5L * 1024 * 1024; // 5 MB

    private final AlternativePaymentService alternativePaymentService;
    private final AlternativePaymentRequestRepository alternativePaymentRequestRepository;
    private final PurchaseRepository purchaseRepository;

    public AlternativePaymentController(AlternativePaymentService alternativePaymentService,
                                         AlternativePaymentRequestRepository alternativePaymentRequestRepository,
                                         PurchaseRepository purchaseRepository) {
        this.alternativePaymentService = alternativePaymentService;
        this.alternativePaymentRequestRepository = alternativePaymentRequestRepository;
        this.purchaseRepository = purchaseRepository;
    }

    /**
     * @Transactional aqui es necesario: AlternativePaymentRequest.purchase
     * y Purchase.user son ambos FetchType.LAZY, y AlternativePaymentResponse.from()
     * los lee DESPUES de que el repositorio ya devolvio (su propia
     * transaccion, mas corta, ya cerro para ese punto). Mismo patron
     * exacto que ya se corrigio antes en ReferralController/
     * CustomUserPrincipal -- ver esos comentarios para el detalle
     * completo del porque.
     */
    @GetMapping
    @Transactional(readOnly = true)
    @Operation(summary = "Ver el estado de mi solicitud de pago manual para esta compra")
    public ResponseEntity<AlternativePaymentResponse> myRequest(@PathVariable UUID purchaseId,
                                                                  @AuthenticationPrincipal CustomUserPrincipal principal) {
        AlternativePaymentRequest request = findOwnRequestOrThrow(purchaseId, principal.getUser());
        return ResponseEntity.ok(AlternativePaymentResponse.from(request));
    }

    @PostMapping(value = "/proof", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional
    @Operation(summary = "Subir el comprobante de pago (imagen o PDF, max 5MB) -- "
            + "solo una vez que el admin aprobo la solicitud (estado APPROVED o PAYMENT_PROOF_PENDING)")
    public ResponseEntity<AlternativePaymentResponse> uploadProof(@PathVariable UUID purchaseId,
                                                                    @AuthenticationPrincipal CustomUserPrincipal principal,
                                                                    @RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new BusinessRuleException("El archivo del comprobante esta vacio");
        }
        if (file.getSize() > MAX_PROOF_SIZE_BYTES) {
            throw new BusinessRuleException("El comprobante no puede superar 5MB");
        }

        AlternativePaymentRequest request = findOwnRequestOrThrow(purchaseId, principal.getUser());
        AlternativePaymentRequest updated = alternativePaymentService.uploadProof(
                request.getId(), principal.getUser(), file.getBytes(), file.getContentType());

        return ResponseEntity.ok(AlternativePaymentResponse.from(updated));
    }

    private AlternativePaymentRequest findOwnRequestOrThrow(UUID purchaseId, User user) {
        Purchase purchase = purchaseRepository.findById(purchaseId)
                .orElseThrow(() -> new ResourceNotFoundException("Compra no encontrada"));
        if (!purchase.getUser().getId().equals(user.getId())) {
            throw new BusinessRuleException("Esta compra no te pertenece");
        }
        return alternativePaymentRequestRepository.findByPurchase(purchase)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Esta compra no tiene una solicitud de pago alternativo asociada"));
    }
}