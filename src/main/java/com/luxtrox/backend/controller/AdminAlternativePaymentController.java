package com.luxtrox.backend.controller;

import com.luxtrox.backend.dto.alternativepayment.AlternativePaymentResponse;
import com.luxtrox.backend.dto.alternativepayment.RejectAlternativePaymentRequest;
import com.luxtrox.backend.entity.AlternativePaymentRequest;
import com.luxtrox.backend.entity.enums.AlternativePaymentStatus;
import com.luxtrox.backend.repository.AlternativePaymentRequestRepository;
import com.luxtrox.backend.security.CustomUserPrincipal;
import com.luxtrox.backend.service.AlternativePaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Lado del ADMIN del flujo de pago manual -- ver
 * AlternativePaymentController para el lado del usuario, y
 * AlternativePaymentService para el detalle completo de la maquina
 * de estados (docs/domain-model.md S3.5).
 *
 * Todos los metodos que devuelven AlternativePaymentResponse son
 * @Transactional: AlternativePaymentResponse.from() recorre
 * request.getPurchase().getUser() (ambos FetchType.LAZY), y para
 * cuando ese metodo corre, la transaccion mas corta del repositorio
 * (o la del service, en approve/confirm/reject) ya cerro. Mismo
 * patron que ya se corrigio en ReferralController -- ver ese
 * comentario para el detalle completo del porque.
 */
@RestController
@RequestMapping("/admin/alternative-payments")
@Tag(name = "Admin - Alternative Payments", description = "Revision y aprobacion de pagos manuales")
public class AdminAlternativePaymentController {

    private final AlternativePaymentService alternativePaymentService;
    private final AlternativePaymentRequestRepository alternativePaymentRequestRepository;

    public AdminAlternativePaymentController(AlternativePaymentService alternativePaymentService,
                                               AlternativePaymentRequestRepository alternativePaymentRequestRepository) {
        this.alternativePaymentService = alternativePaymentService;
        this.alternativePaymentRequestRepository = alternativePaymentRequestRepository;
    }

    @GetMapping
    @Transactional(readOnly = true)
    @Operation(summary = "Listar solicitudes por estado -- por defecto, las que esperan revision (UNDER_REVIEW)")
    public ResponseEntity<List<AlternativePaymentResponse>> list(
            @RequestParam(required = false) AlternativePaymentStatus status) {
        AlternativePaymentStatus effectiveStatus = status != null ? status : AlternativePaymentStatus.UNDER_REVIEW;
        List<AlternativePaymentResponse> response = alternativePaymentRequestRepository
                .findByStatus(effectiveStatus).stream()
                .map(AlternativePaymentResponse::from)
                .toList();
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/approve")
    @Transactional
    @Operation(summary = "Aprobar la solicitud inicial (REQUESTED -> APPROVED) -- "
            + "habilita al usuario a subir su comprobante")
    public ResponseEntity<AlternativePaymentResponse> approve(@PathVariable UUID id,
                                                                @AuthenticationPrincipal CustomUserPrincipal principal) {
        AlternativePaymentRequest request = alternativePaymentService.approve(id, principal.getUser());
        return ResponseEntity.ok(AlternativePaymentResponse.from(request));
    }

    @GetMapping(value = "/{id}/proof", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    @Operation(summary = "Descargar el comprobante subido, para revisarlo")
    public ResponseEntity<byte[]> downloadProof(@PathVariable UUID id) {
        byte[] bytes = alternativePaymentService.downloadProof(id);
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"comprobante-" + id + "\"")
                .body(bytes);
    }

    @PostMapping("/{id}/confirm")
    @Transactional
    @Operation(summary = "Confirmar el comprobante (UNDER_REVIEW -> CONFIRMED) -- "
            + "dispara la misma confirmacion de compra que cualquier otro metodo de pago")
    public ResponseEntity<AlternativePaymentResponse> confirm(@PathVariable UUID id,
                                                                @AuthenticationPrincipal CustomUserPrincipal principal) {
        AlternativePaymentRequest request = alternativePaymentService.confirm(id, principal.getUser());
        return ResponseEntity.ok(AlternativePaymentResponse.from(request));
    }

    @PostMapping("/{id}/reject")
    @Transactional
    @Operation(summary = "Rechazar el comprobante (UNDER_REVIEW -> REJECTED) -- requiere explicar por que")
    public ResponseEntity<AlternativePaymentResponse> reject(@PathVariable UUID id,
                                                               @AuthenticationPrincipal CustomUserPrincipal principal,
                                                               @Valid @RequestBody RejectAlternativePaymentRequest body) {
        AlternativePaymentRequest request = alternativePaymentService.reject(id, principal.getUser(), body.adminNotes());
        return ResponseEntity.ok(AlternativePaymentResponse.from(request));
    }

    @PostMapping("/expire-overdue")
    @Operation(summary = "Marcar como EXPIRED las solicitudes que llevan mas de 72h en REQUESTED sin aprobarse")
    public ResponseEntity<Integer> expireOverdue() {
        return ResponseEntity.ok(alternativePaymentService.expireOverdueRequests());
    }
}