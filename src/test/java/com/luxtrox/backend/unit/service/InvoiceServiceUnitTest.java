package com.luxtrox.backend.unit.service;

import com.luxtrox.backend.entity.Invoice;
import com.luxtrox.backend.entity.Purchase;
import com.luxtrox.backend.entity.Role;
import com.luxtrox.backend.entity.User;
import com.luxtrox.backend.entity.enums.PaymentMethod;
import com.luxtrox.backend.entity.enums.PlanType;
import com.luxtrox.backend.integration.storage.SupabaseStorageClient;
import com.luxtrox.backend.repository.InvoiceRepository;
import com.luxtrox.backend.service.InvoiceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unitario puro con Mockito. La generacion del PDF en si usa OpenPDF
 * de verdad (no se mockea -- es una libreria determinista, no una
 * dependencia inyectada), solo se mockean el repositorio y el cliente
 * de storage.
 */
@ExtendWith(MockitoExtension.class)
class InvoiceServiceUnitTest {

    @Mock private InvoiceRepository invoiceRepository;
    @Mock private SupabaseStorageClient storageClient;

    private InvoiceService service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new InvoiceService(invoiceRepository, storageClient);

        Role role = new Role("USER", "Usuario estandar");
        user = new User("Carlos Mendoza", "carlos@example.com", "+1", "hash", role, "CARLOS01");
        setId(user, UUID.randomUUID());

        lenient().when(invoiceRepository.save(any(Invoice.class)))
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

    private void setField(Object entity, String fieldName, Object value) {
        try {
            var field = entity.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(entity, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Purchase confirmedPurchase() {
        Purchase purchase = new Purchase(user, PlanType.DRIVER, 2, new BigDecimal("2198.00"), PaymentMethod.CRYPTO);
        setId(purchase, UUID.randomUUID());
        // createdAt solo se llena via @PrePersist -- sin persistir de
        // verdad hay que setearlo a mano, igual que el id.
        setField(purchase, "createdAt", OffsetDateTime.now());
        purchase.setConfirmedAt(OffsetDateTime.now());
        return purchase;
    }

    @Test
    void generateStoreAndReturnBytes_happyPath_uploadsAValidPdfAndSavesInvoice() {
        Purchase purchase = confirmedPurchase();

        InvoiceService.InvoiceWithBytes result = service.generateStoreAndReturnBytes(purchase);

        assertThat(result.pdfBytes()).isNotEmpty();
        // %PDF es el "magic header" de cualquier archivo PDF valido.
        assertThat(new String(result.pdfBytes(), 0, 4)).isEqualTo("%PDF");

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> contentTypeCaptor = ArgumentCaptor.forClass(String.class);
        verify(storageClient).uploadFile(keyCaptor.capture(), eq(result.pdfBytes()), contentTypeCaptor.capture());
        assertThat(keyCaptor.getValue()).startsWith("invoices/").endsWith(".pdf");
        assertThat(contentTypeCaptor.getValue()).isEqualTo("application/pdf");

        ArgumentCaptor<Invoice> invoiceCaptor = ArgumentCaptor.forClass(Invoice.class);
        verify(invoiceRepository).save(invoiceCaptor.capture());
        assertThat(invoiceCaptor.getValue().getPurchase()).isEqualTo(purchase);
        assertThat(invoiceCaptor.getValue().getPdfStorageKey()).isEqualTo(keyCaptor.getValue());

        assertThat(result.invoice()).isEqualTo(invoiceCaptor.getValue());
    }

    @Test
    void generateAndStore_delegatesToTheSameLogicButReturnsOnlyTheInvoice() {
        Purchase purchase = confirmedPurchase();

        Invoice invoice = service.generateAndStore(purchase);

        assertThat(invoice.getPurchase()).isEqualTo(purchase);
        verify(storageClient).uploadFile(any(), any(), eq("application/pdf"));
        verify(invoiceRepository).save(any(Invoice.class));
    }

    @Test
    void consecutiveInvoices_getDifferentInvoiceNumbers() {
        Purchase purchaseA = confirmedPurchase();
        Purchase purchaseB = confirmedPurchase();

        Invoice invoiceA = service.generateAndStore(purchaseA);
        Invoice invoiceB = service.generateAndStore(purchaseB);

        assertThat(invoiceA.getInvoiceNumber()).isNotEqualTo(invoiceB.getInvoiceNumber());
        assertThat(invoiceA.getInvoiceNumber()).startsWith("LUX-");
    }

    @Test
    void downloadPdf_delegatesToStorageClientWithTheInvoicesStorageKey() {
        Purchase purchase = confirmedPurchase();
        Invoice invoice = new Invoice(purchase, "LUX-2026-1", "invoices/LUX-2026-1.pdf");
        byte[] fakeBytes = "contenido-falso".getBytes();
        when(storageClient.downloadFile("invoices/LUX-2026-1.pdf")).thenReturn(fakeBytes);

        byte[] result = service.downloadPdf(invoice);

        assertThat(result).isEqualTo(fakeBytes);
    }
}
