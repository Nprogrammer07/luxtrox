package com.luxtrox.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.luxtrox.backend.entity.Purchase;
import com.luxtrox.backend.exception.ResourceNotFoundException;
import com.luxtrox.backend.integration.nowpayments.NowPaymentsProperties;
import com.luxtrox.backend.integration.nowpayments.NowPaymentsSignatureVerifier;
import com.luxtrox.backend.integration.nowpayments.dto.IpnCallbackPayload;
import com.luxtrox.backend.repository.PurchaseRepository;
import com.luxtrox.backend.service.PurchaseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Recibe el callback IPN de NOWPayments cuando el estado de un pago
 * cambia. Vive fuera de /admin y de /auth -- ver SecurityConfig, esta
 * ruta especifica esta en la lista de permitAll() porque NOWPayments
 * no manda ningun JWT, solo la firma HMAC propia (ver
 * NowPaymentsSignatureVerifier).
 *
 * IMPORTANTE: la firma se valida sobre el BODY CRUDO (String), nunca
 * sobre un objeto ya parseado -- por eso el parametro es String, no un
 * DTO directamente.
 */
@RestController
@RequestMapping("/webhooks/nowpayments")
@Tag(name = "Webhooks", description = "Callbacks de proveedores externos (NOWPayments)")
public class NowPaymentsWebhookController {

    private static final Logger log = LoggerFactory.getLogger(NowPaymentsWebhookController.class);

    private final NowPaymentsSignatureVerifier signatureVerifier;
    private final NowPaymentsProperties properties;
    private final PurchaseRepository purchaseRepository;
    private final PurchaseService purchaseService;
    private final ObjectMapper objectMapper;

    public NowPaymentsWebhookController(NowPaymentsSignatureVerifier signatureVerifier,
                                         NowPaymentsProperties properties,
                                         PurchaseRepository purchaseRepository,
                                         PurchaseService purchaseService,
                                         ObjectMapper objectMapper) {
        this.signatureVerifier = signatureVerifier;
        this.properties = properties;
        this.purchaseRepository = purchaseRepository;
        this.purchaseService = purchaseService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/ipn")
    @Operation(summary = "Callback IPN de NOWPayments (uso interno del proveedor, no de un usuario)")
    public ResponseEntity<Void> handleIpn(@RequestHeader(value = "x-nowpayments-sig", required = false) String signature,
                                           @RequestBody String rawBody) {

        if (!signatureVerifier.isValid(rawBody, signature, properties.getIpnSecret())) {
            log.warn("IPN de NOWPayments con firma invalida -- rechazado");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        IpnCallbackPayload payload;
        try {
            payload = objectMapper.readValue(rawBody, IpnCallbackPayload.class);
        } catch (Exception e) {
            log.warn("IPN de NOWPayments con firma valida pero JSON no parseable: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }

        if (!payload.isSuccessfullyPaid()) {
            // Estados intermedios (confirming, sending, etc.) -- no es
            // un error, simplemente todavia no hay nada que confirmar.
            log.info("IPN de NOWPayments para order_id={} con estado intermedio: {}",
                    payload.orderId(), payload.paymentStatus());
            return ResponseEntity.ok().build();
        }

        UUID purchaseId;
        try {
            purchaseId = UUID.fromString(payload.orderId());
        } catch (IllegalArgumentException e) {
            log.error("IPN de NOWPayments con order_id que no es un UUID valido: {}", payload.orderId());
            return ResponseEntity.badRequest().build();
        }

        Purchase purchase = purchaseRepository.findById(purchaseId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "IPN de NOWPayments referencia una compra inexistente: " + purchaseId));

        purchaseService.confirmPurchase(purchase.getId());

        return ResponseEntity.ok().build();
    }
}
