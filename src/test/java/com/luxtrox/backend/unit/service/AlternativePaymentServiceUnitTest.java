package com.luxtrox.backend.unit.service;

import com.luxtrox.backend.entity.*;
import com.luxtrox.backend.entity.enums.*;
import com.luxtrox.backend.exception.BusinessRuleException;
import com.luxtrox.backend.exception.ResourceNotFoundException;
import com.luxtrox.backend.integration.storage.SupabaseStorageClient;
import com.luxtrox.backend.repository.AlternativePaymentRequestRepository;
import com.luxtrox.backend.repository.PurchaseRepository;
import com.luxtrox.backend.service.AlternativePaymentService;
import com.luxtrox.backend.service.AuditService;
import com.luxtrox.backend.service.PurchaseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Cubre la maquina de estados completa de AlternativePaymentService
 * (ver docs/domain-model.md S3.5).
 */
@ExtendWith(MockitoExtension.class)
class AlternativePaymentServiceUnitTest {

    @Mock private AlternativePaymentRequestRepository alternativePaymentRequestRepository;
    @Mock private PurchaseRepository purchaseRepository;
    @Mock private SupabaseStorageClient storageClient;
    @Mock private PurchaseService purchaseService;
    @Mock private AuditService auditService;

    private AlternativePaymentService service;
    private User buyer;
    private User admin;
    private Purchase purchase;

    @BeforeEach
    void setUp() {
        service = new AlternativePaymentService(alternativePaymentRequestRepository, purchaseRepository,
                storageClient, purchaseService, auditService);

        Role role = new Role("USER", "Usuario estandar");
        buyer = new User("Carlos", "carlos@example.com", "+1", "hash", role, "CARLOS01");
        setId(buyer, UUID.randomUUID());

        Role adminRole = new Role("ADMIN", "Administrador");
        admin = new User("Admin", "admin@example.com", "+1", "hash", adminRole, "ADMIN001");
        setId(admin, UUID.randomUUID());

        purchase = new Purchase(buyer, PlanType.ZENITH, 1, new BigDecimal("1099.00"), PaymentMethod.ALTERNATIVE);
        setId(purchase, UUID.randomUUID());

        lenient().when(alternativePaymentRequestRepository.save(any(AlternativePaymentRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private void setId(Object entity, UUID id) {
        try {
            var field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private AlternativePaymentRequest requestWithStatus(AlternativePaymentStatus status) {
        AlternativePaymentRequest request = new AlternativePaymentRequest(purchase, OffsetDateTime.now().plusHours(72));
        setId(request, UUID.randomUUID());
        request.setStatus(status);
        return request;
    }

    // ---------- createRequest() ----------

    @Test
    void createRequest_happyPath_setsStatusRequestedAndExpiresIn72Hours() {
        AlternativePaymentRequest result = service.createRequest(purchase);

        assertThat(result.getStatus()).isEqualTo(AlternativePaymentStatus.REQUESTED);
        assertThat(result.getExpiresAt()).isCloseTo(OffsetDateTime.now().plusHours(72),
                org.assertj.core.api.Assertions.within(5, java.time.temporal.ChronoUnit.SECONDS));
    }

    @Test
    void createRequest_wrongPaymentMethod_throws() {
        Purchase cryptoPurchase = new Purchase(buyer, PlanType.ZENITH, 1, new BigDecimal("1099.00"), PaymentMethod.CRYPTO);

        assertThrows(BusinessRuleException.class, () -> service.createRequest(cryptoPurchase));
        verifyNoInteractions(alternativePaymentRequestRepository);
    }

    // ---------- approve() ----------

    @Test
    void approve_happyPath_movesToApproved() {
        AlternativePaymentRequest request = requestWithStatus(AlternativePaymentStatus.REQUESTED);
        when(alternativePaymentRequestRepository.findById(request.getId())).thenReturn(Optional.of(request));

        AlternativePaymentRequest result = service.approve(request.getId(), admin);

        assertThat(result.getStatus()).isEqualTo(AlternativePaymentStatus.APPROVED);
    }

    @Test
    void approve_notRequested_throws() {
        AlternativePaymentRequest request = requestWithStatus(AlternativePaymentStatus.UNDER_REVIEW);
        when(alternativePaymentRequestRepository.findById(request.getId())).thenReturn(Optional.of(request));

        assertThrows(BusinessRuleException.class, () -> service.approve(request.getId(), admin));
    }

    @Test
    void approve_unknownId_throwsResourceNotFound() {
        UUID id = UUID.randomUUID();
        when(alternativePaymentRequestRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.approve(id, admin));
    }

    // ---------- uploadProof() ----------

    @Test
    void uploadProof_fromApproved_uploadsAndMovesToUnderReview() {
        AlternativePaymentRequest request = requestWithStatus(AlternativePaymentStatus.APPROVED);
        when(alternativePaymentRequestRepository.findById(request.getId())).thenReturn(Optional.of(request));

        AlternativePaymentRequest result = service.uploadProof(request.getId(), buyer, "contenido".getBytes(), "image/png");

        assertThat(result.getStatus()).isEqualTo(AlternativePaymentStatus.UNDER_REVIEW);
        assertThat(result.getPaymentProofStorageKey()).startsWith("payment-proofs/").endsWith(".png");
        verify(storageClient).uploadFile(eq(result.getPaymentProofStorageKey()), any(byte[].class), eq("image/png"));
    }

    @Test
    void uploadProof_fromPaymentProofPending_alsoAccepted() {
        AlternativePaymentRequest request = requestWithStatus(AlternativePaymentStatus.PAYMENT_PROOF_PENDING);
        when(alternativePaymentRequestRepository.findById(request.getId())).thenReturn(Optional.of(request));

        AlternativePaymentRequest result = service.uploadProof(request.getId(), buyer, "x".getBytes(), "application/pdf");

        assertThat(result.getStatus()).isEqualTo(AlternativePaymentStatus.UNDER_REVIEW);
        assertThat(result.getPaymentProofStorageKey()).endsWith(".pdf");
    }

    @Test
    void uploadProof_wrongOwner_throwsAndNeverUploads() {
        AlternativePaymentRequest request = requestWithStatus(AlternativePaymentStatus.APPROVED);
        when(alternativePaymentRequestRepository.findById(request.getId())).thenReturn(Optional.of(request));

        User otherUser = new User("Otro", "otro@example.com", "+1", "hash",
                new Role("USER", "Usuario estandar"), "OTRO0001");
        setId(otherUser, UUID.randomUUID());

        assertThrows(BusinessRuleException.class,
                () -> service.uploadProof(request.getId(), otherUser, "x".getBytes(), "image/png"));
        verifyNoInteractions(storageClient);
    }

    @Test
    void uploadProof_stillRequested_throws() {
        AlternativePaymentRequest request = requestWithStatus(AlternativePaymentStatus.REQUESTED);
        when(alternativePaymentRequestRepository.findById(request.getId())).thenReturn(Optional.of(request));

        assertThrows(BusinessRuleException.class,
                () -> service.uploadProof(request.getId(), buyer, "x".getBytes(), "image/png"));
        verifyNoInteractions(storageClient);
    }

    // ---------- confirm() ----------

    @Test
    void confirm_happyPath_movesToConfirmedAndDelegatesToPurchaseService() {
        AlternativePaymentRequest request = requestWithStatus(AlternativePaymentStatus.UNDER_REVIEW);
        when(alternativePaymentRequestRepository.findById(request.getId())).thenReturn(Optional.of(request));

        AlternativePaymentRequest result = service.confirm(request.getId(), admin);

        assertThat(result.getStatus()).isEqualTo(AlternativePaymentStatus.CONFIRMED);
        assertThat(result.getReviewedByAdmin()).isEqualTo(admin);
        assertThat(result.getReviewedAt()).isNotNull();
        verify(purchaseService).confirmPurchase(purchase.getId());
    }

    @Test
    void confirm_notUnderReview_throwsAndNeverCallsPurchaseService() {
        AlternativePaymentRequest request = requestWithStatus(AlternativePaymentStatus.APPROVED);
        when(alternativePaymentRequestRepository.findById(request.getId())).thenReturn(Optional.of(request));

        assertThrows(BusinessRuleException.class, () -> service.confirm(request.getId(), admin));
        verifyNoInteractions(purchaseService);
    }

    // ---------- reject() ----------

    @Test
    void reject_happyPath_movesToRejectedAndRejectsThePurchaseToo() {
        AlternativePaymentRequest request = requestWithStatus(AlternativePaymentStatus.UNDER_REVIEW);
        when(alternativePaymentRequestRepository.findById(request.getId())).thenReturn(Optional.of(request));

        AlternativePaymentRequest result = service.reject(request.getId(), admin, "Comprobante ilegible");

        assertThat(result.getStatus()).isEqualTo(AlternativePaymentStatus.REJECTED);
        assertThat(result.getAdminNotes()).isEqualTo("Comprobante ilegible");

        ArgumentCaptor<Purchase> purchaseCaptor = ArgumentCaptor.forClass(Purchase.class);
        verify(purchaseRepository).save(purchaseCaptor.capture());
        assertThat(purchaseCaptor.getValue().getStatus()).isEqualTo(PurchaseStatus.REJECTED);
    }

    @Test
    void reject_notUnderReview_throws() {
        AlternativePaymentRequest request = requestWithStatus(AlternativePaymentStatus.REQUESTED);
        when(alternativePaymentRequestRepository.findById(request.getId())).thenReturn(Optional.of(request));

        assertThrows(BusinessRuleException.class, () -> service.reject(request.getId(), admin, "notas"));
        verify(purchaseRepository, never()).save(any());
    }

    // ---------- expireOverdueRequests() ----------

    @Test
    void expireOverdueRequests_onlyTouchesRequestedPastDeadline() {
        AlternativePaymentRequest overdue = requestWithStatus(AlternativePaymentStatus.REQUESTED);
        overdue.setStatus(AlternativePaymentStatus.REQUESTED);
        setExpiresAt(overdue, OffsetDateTime.now().minusHours(1));

        AlternativePaymentRequest stillValid = requestWithStatus(AlternativePaymentStatus.REQUESTED);
        setExpiresAt(stillValid, OffsetDateTime.now().plusHours(1));

        when(alternativePaymentRequestRepository.findByStatus(AlternativePaymentStatus.REQUESTED))
                .thenReturn(List.of(overdue, stillValid));

        int expiredCount = service.expireOverdueRequests();

        assertThat(expiredCount).isEqualTo(1);
        assertThat(overdue.getStatus()).isEqualTo(AlternativePaymentStatus.EXPIRED);
        assertThat(stillValid.getStatus()).isEqualTo(AlternativePaymentStatus.REQUESTED); // no se toco

        ArgumentCaptor<Purchase> purchaseCaptor = ArgumentCaptor.forClass(Purchase.class);
        verify(purchaseRepository, times(1)).save(purchaseCaptor.capture());
        assertThat(purchaseCaptor.getValue().getStatus()).isEqualTo(PurchaseStatus.EXPIRED);
    }

    private void setExpiresAt(AlternativePaymentRequest request, OffsetDateTime expiresAt) {
        try {
            var field = AlternativePaymentRequest.class.getDeclaredField("expiresAt");
            field.setAccessible(true);
            field.set(request, expiresAt);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void expireOverdueRequests_noneOverdue_returnsZero() {
        when(alternativePaymentRequestRepository.findByStatus(AlternativePaymentStatus.REQUESTED))
                .thenReturn(List.of());

        assertThat(service.expireOverdueRequests()).isEqualTo(0);
        verifyNoInteractions(purchaseRepository);
    }

    // ---------- downloadProof() ----------

    @Test
    void downloadProof_happyPath_delegatesToStorageClient() {
        AlternativePaymentRequest request = requestWithStatus(AlternativePaymentStatus.UNDER_REVIEW);
        request.setPaymentProofStorageKey("payment-proofs/abc.png");
        when(alternativePaymentRequestRepository.findById(request.getId())).thenReturn(Optional.of(request));
        byte[] fakeBytes = "fake".getBytes();
        when(storageClient.downloadFile("payment-proofs/abc.png")).thenReturn(fakeBytes);

        assertThat(service.downloadProof(request.getId())).isEqualTo(fakeBytes);
    }

    @Test
    void downloadProof_noProofUploadedYet_throws() {
        AlternativePaymentRequest request = requestWithStatus(AlternativePaymentStatus.APPROVED);
        when(alternativePaymentRequestRepository.findById(request.getId())).thenReturn(Optional.of(request));

        assertThrows(BusinessRuleException.class, () -> service.downloadProof(request.getId()));
        verifyNoInteractions(storageClient);
    }
}