package com.luxtrox.backend.service;

import com.luxtrox.backend.entity.AlternativePaymentRequest;
import com.luxtrox.backend.entity.Purchase;
import com.luxtrox.backend.entity.User;
import com.luxtrox.backend.entity.enums.AlternativePaymentStatus;
import com.luxtrox.backend.entity.enums.PaymentMethod;
import com.luxtrox.backend.entity.enums.PurchaseStatus;
import com.luxtrox.backend.exception.BusinessRuleException;
import com.luxtrox.backend.exception.ResourceNotFoundException;
import com.luxtrox.backend.integration.storage.SupabaseStorageClient;
import com.luxtrox.backend.repository.AlternativePaymentRequestRepository;
import com.luxtrox.backend.repository.PurchaseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Implementa el flujo manual de pago (no-cripto) descrito en
 * docs/domain-model.md S3.5:
 *
 * <pre>
 * REQUESTED -&gt; APPROVED -&gt; PAYMENT_PROOF_PENDING -&gt; UNDER_REVIEW -&gt; CONFIRMED
 *                                                                 \-&gt; REJECTED
 * REQUESTED -&gt; EXPIRED   (72h sin avanzar)
 * </pre>
 *
 * NOTA SOBRE UNA AMBIGUEDAD DEL DISENO ORIGINAL: el diagrama no
 * especifica que accion DISTINTA mueve APPROVED -&gt; PAYMENT_PROOF_PENDING
 * (a diferencia de las demas flechas, que si tienen un disparador
 * claro: aprobar, subir comprobante, confirmar, rechazar, o que pasen
 * 72h). La interpretacion que se uso aqui: approve() deja la solicitud
 * en APPROVED, y uploadProof() acepta como precondicion valida TANTO
 * APPROVED como PAYMENT_PROOF_PENDING (los trata como equivalentes
 * para ese proposito) -- evita inventar un tercer endpoint sin una
 * accion clara que lo justifique, sin dejar de honrar los dos valores
 * tal como estan documentados. Si la intencion original era otra,
 * corregir aqui.
 */
@Service
public class AlternativePaymentService {

    private static final long EXPIRATION_HOURS = 72;

    private final AlternativePaymentRequestRepository alternativePaymentRequestRepository;
    private final PurchaseRepository purchaseRepository;
    private final SupabaseStorageClient storageClient;
    private final PurchaseService purchaseService;
    private final AuditService auditService;

    public AlternativePaymentService(AlternativePaymentRequestRepository alternativePaymentRequestRepository,
                                      PurchaseRepository purchaseRepository,
                                      SupabaseStorageClient storageClient,
                                      PurchaseService purchaseService,
                                      AuditService auditService) {
        this.alternativePaymentRequestRepository = alternativePaymentRequestRepository;
        this.purchaseRepository = purchaseRepository;
        this.storageClient = storageClient;
        this.purchaseService = purchaseService;
        this.auditService = auditService;
    }

    /**
     * Se llama justo despues de crear una Purchase con
     * paymentMethod=ALTERNATIVE (ver PurchaseController) -- nunca de
     * forma independiente.
     */
    @Transactional
    public AlternativePaymentRequest createRequest(Purchase purchase) {
        if (purchase.getPaymentMethod() != PaymentMethod.ALTERNATIVE) {
            throw new BusinessRuleException("createRequest solo aplica a compras con paymentMethod=ALTERNATIVE");
        }
        AlternativePaymentRequest request = new AlternativePaymentRequest(
                purchase, OffsetDateTime.now().plusHours(EXPIRATION_HOURS));
        return alternativePaymentRequestRepository.save(request);
    }

    @Transactional
    public AlternativePaymentRequest approve(UUID requestId, User admin) {
        AlternativePaymentRequest request = findOrThrow(requestId);
        if (request.getStatus() != AlternativePaymentStatus.REQUESTED) {
            throw new BusinessRuleException(
                    "Solo se puede aprobar una solicitud REQUESTED (estado actual: " + request.getStatus() + ")");
        }
        request.setStatus(AlternativePaymentStatus.APPROVED);
        alternativePaymentRequestRepository.save(request);

        auditService.record(admin, "AlternativePaymentRequest", request.getId(), "APPROVED",
                AlternativePaymentStatus.REQUESTED, request.getStatus());
        return request;
    }

    /**
     * Acepta tanto APPROVED como PAYMENT_PROOF_PENDING como estado de
     * partida -- ver la nota de ambiguedad en el javadoc de la clase.
     */
    @Transactional
    public AlternativePaymentRequest uploadProof(UUID requestId, User user, byte[] proofBytes, String contentType) {
        AlternativePaymentRequest request = findOrThrow(requestId);

        if (!request.getPurchase().getUser().getId().equals(user.getId())) {
            throw new BusinessRuleException("Esta solicitud de pago no te pertenece");
        }
        if (request.getStatus() != AlternativePaymentStatus.APPROVED
                && request.getStatus() != AlternativePaymentStatus.PAYMENT_PROOF_PENDING) {
            throw new BusinessRuleException(
                    "Solo se puede subir el comprobante cuando la solicitud ya fue aprobada (estado actual: "
                            + request.getStatus() + ")");
        }

        String key = "payment-proofs/" + request.getId() + "-" + UUID.randomUUID() + extensionFor(contentType);
        storageClient.uploadFile(key, proofBytes, contentType);

        AlternativePaymentStatus oldStatus = request.getStatus();
        request.setPaymentProofStorageKey(key);
        request.setStatus(AlternativePaymentStatus.UNDER_REVIEW);
        alternativePaymentRequestRepository.save(request);

        auditService.record(user, "AlternativePaymentRequest", request.getId(), "PROOF_UPLOADED",
                oldStatus, request.getStatus());
        return request;
    }

    /**
     * Confirma el comprobante -- dispara la MISMA orquestacion que
     * cualquier otra confirmacion de compra (crear posicion/licencia,
     * factura, correo, evaluacion de referidos), reutilizando
     * PurchaseService.confirmPurchase() en vez de duplicar esa logica.
     */
    @Transactional
    public AlternativePaymentRequest confirm(UUID requestId, User admin) {
        AlternativePaymentRequest request = findOrThrow(requestId);
        if (request.getStatus() != AlternativePaymentStatus.UNDER_REVIEW) {
            throw new BusinessRuleException(
                    "Solo se puede confirmar una solicitud UNDER_REVIEW (estado actual: " + request.getStatus() + ")");
        }

        request.setStatus(AlternativePaymentStatus.CONFIRMED);
        request.setReviewedByAdmin(admin);
        request.setReviewedAt(OffsetDateTime.now());
        alternativePaymentRequestRepository.save(request);

        purchaseService.confirmPurchase(request.getPurchase().getId());

        auditService.record(admin, "AlternativePaymentRequest", request.getId(), "CONFIRMED",
                AlternativePaymentStatus.UNDER_REVIEW, request.getStatus());
        return request;
    }

    /**
     * Solo aplica desde UNDER_REVIEW (ver diagrama -- la flecha de
     * rechazo sale exactamente de ahi). Rechazar tambien marca la
     * Purchase subyacente como REJECTED -- su propia maquina de
     * estados (docs/domain-model.md S3.1) permite PENDING -&gt; REJECTED.
     */
    @Transactional
    public AlternativePaymentRequest reject(UUID requestId, User admin, String adminNotes) {
        AlternativePaymentRequest request = findOrThrow(requestId);
        if (request.getStatus() != AlternativePaymentStatus.UNDER_REVIEW) {
            throw new BusinessRuleException(
                    "Solo se puede rechazar una solicitud UNDER_REVIEW (estado actual: " + request.getStatus() + ")");
        }

        request.setStatus(AlternativePaymentStatus.REJECTED);
        request.setReviewedByAdmin(admin);
        request.setReviewedAt(OffsetDateTime.now());
        request.setAdminNotes(adminNotes);
        alternativePaymentRequestRepository.save(request);

        Purchase purchase = request.getPurchase();
        purchase.setStatus(PurchaseStatus.REJECTED);
        purchaseRepository.save(purchase);

        auditService.record(admin, "AlternativePaymentRequest", request.getId(), "REJECTED",
                AlternativePaymentStatus.UNDER_REVIEW, request.getStatus());
        return request;
    }

    /**
     * Solo expira solicitudes que SIGUEN en REQUESTED -- tal como el
     * diagrama lo especifica (la flecha de expiracion sale unicamente
     * de ahi). Una vez que el admin la aprueba, el reloj de 72h ya no
     * aplica segun el diseno original. Llamado por un admin via API
     * (mismo patron que ZenithService.expireOverdueLicenses()) -- no
     * hay un scheduler/cron en este proyecto todavia.
     */
    @Transactional
    public int expireOverdueRequests() {
        List<AlternativePaymentRequest> overdue = alternativePaymentRequestRepository
                .findByStatus(AlternativePaymentStatus.REQUESTED).stream()
                .filter(r -> r.getExpiresAt().isBefore(OffsetDateTime.now()))
                .toList();

        for (AlternativePaymentRequest request : overdue) {
            request.setStatus(AlternativePaymentStatus.EXPIRED);
            alternativePaymentRequestRepository.save(request);

            Purchase purchase = request.getPurchase();
            purchase.setStatus(PurchaseStatus.EXPIRED);
            purchaseRepository.save(purchase);

            auditService.recordSystemAction("AlternativePaymentRequest", request.getId(), "EXPIRED",
                    AlternativePaymentStatus.REQUESTED, AlternativePaymentStatus.EXPIRED);
        }
        return overdue.size();
    }

    private AlternativePaymentRequest findOrThrow(UUID id) {
        return alternativePaymentRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Solicitud de pago alternativo no encontrada"));
    }

    public byte[] downloadProof(UUID requestId) {
        AlternativePaymentRequest request = findOrThrow(requestId);
        if (request.getPaymentProofStorageKey() == null) {
            throw new BusinessRuleException("Esta solicitud todavia no tiene un comprobante subido");
        }
        return storageClient.downloadFile(request.getPaymentProofStorageKey());
    }

    private String extensionFor(String contentType) {
        if (contentType == null) {
            return "";
        }
        return switch (contentType) {
            case "image/png" -> ".png";
            case "image/jpeg", "image/jpg" -> ".jpg";
            case "application/pdf" -> ".pdf";
            default -> "";
        };
    }
}