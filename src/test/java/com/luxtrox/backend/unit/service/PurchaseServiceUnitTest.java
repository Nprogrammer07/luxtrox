package com.luxtrox.backend.unit.service;

import com.luxtrox.backend.entity.*;
import com.luxtrox.backend.entity.enums.PaymentMethod;
import com.luxtrox.backend.entity.enums.PlanType;
import com.luxtrox.backend.entity.enums.PurchaseStatus;
import com.luxtrox.backend.exception.BusinessRuleException;
import com.luxtrox.backend.exception.ResourceNotFoundException;
import com.luxtrox.backend.integration.nowpayments.NowPaymentsClient;
import com.luxtrox.backend.integration.nowpayments.dto.CreateInvoiceResponse;
import com.luxtrox.backend.repository.PurchaseRepository;
import com.luxtrox.backend.repository.UserRepository;
import com.luxtrox.backend.repository.ZenithLicenseRepository;
import com.luxtrox.backend.service.*;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PurchaseServiceUnitTest {

    @Mock private PurchaseRepository purchaseRepository;
    @Mock private UserRepository userRepository;
    @Mock private ZenithLicenseRepository zenithLicenseRepository;
    @Mock private ReferralService referralService;
    @Mock private AuditService auditService;
    @Mock private NowPaymentsClient nowPaymentsClient;
    @Mock private InvoiceService invoiceService;
    @Mock private NotificationEmailService notificationEmailService;
    @Mock private PlusService plusService;

    private PurchaseService purchaseService;
    private MeterRegistry meterRegistry;
    private User user;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        purchaseService = new PurchaseService(purchaseRepository, userRepository,
                zenithLicenseRepository, referralService, auditService, nowPaymentsClient,
                invoiceService, notificationEmailService, meterRegistry, plusService);

        Role role = new Role("USER", "Usuario estandar");
        user = new User("Carlos", "carlos@example.com", "+1", "hash", role, "CARLOS01");
        setId(user, UUID.randomUUID());

        lenient().when(plusService.hasActiveLicense(any())).thenReturn(false);
        lenient().when(purchaseRepository.save(any(Purchase.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    private void setId(Object entity, UUID id) {
        try {
            var f = entity.getClass().getDeclaredField("id");
            f.setAccessible(true);
            f.set(entity, id);
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    // ---------- createZenithPurchase() ----------

    @Test
    void createZenithPurchase_withoutPlus_fullPrice() {
        Purchase result = purchaseService.createZenithPurchase(user, PaymentMethod.CRYPTO);
        assertThat(result.getPlanType()).isEqualTo(PlanType.ZENITH);
        assertThat(result.getTotalAmount()).isEqualByComparingTo(PlanPricing.ZENITH_PRICE);
    }

    @Test
    void createZenithPurchase_withActivePlus_appliesDiscount() {
        when(plusService.hasActiveLicense(user)).thenReturn(true);
        Purchase result = purchaseService.createZenithPurchase(user, PaymentMethod.CRYPTO);
        assertThat(result.getTotalAmount())
                .isEqualByComparingTo(PlanPricing.ZENITH_PRICE.subtract(PlanPricing.PLUS_ZENITH_DISCOUNT));
    }

    // ---------- createPlusPurchase() ----------

    @Test
    void createPlusPurchase_price200_planTypePlus() {
        Purchase result = purchaseService.createPlusPurchase(user, PaymentMethod.ALTERNATIVE);
        assertThat(result.getPlanType()).isEqualTo(PlanType.PLUS);
        assertThat(result.getTotalAmount()).isEqualByComparingTo(PlanPricing.PLUS_PRICE);
    }

    // ---------- initiateCryptoPayment() ----------

    @Test
    void initiateCryptoPayment_happyPath() {
        Purchase purchase = new Purchase(user, PlanType.ZENITH, 1, PlanPricing.ZENITH_PRICE, PaymentMethod.CRYPTO);
        CreateInvoiceResponse fakeInvoice = new CreateInvoiceResponse("inv-1", "https://pay.io/inv-1", "ref");
        when(nowPaymentsClient.createInvoice(purchase)).thenReturn(fakeInvoice);

        String url = purchaseService.initiateCryptoPayment(purchase);
        assertThat(url).isEqualTo("https://pay.io/inv-1");
    }

    @Test
    void initiateCryptoPayment_nonCrypto_throws() {
        Purchase purchase = new Purchase(user, PlanType.ZENITH, 1, PlanPricing.ZENITH_PRICE, PaymentMethod.ALTERNATIVE);
        assertThrows(BusinessRuleException.class, () -> purchaseService.initiateCryptoPayment(purchase));
        verifyNoInteractions(nowPaymentsClient);
    }

    // ---------- confirmPurchase() ----------

    @Test
    void confirmPurchase_alreadyConfirmed_isIdempotent() {
        Purchase purchase = new Purchase(user, PlanType.ZENITH, 1, PlanPricing.ZENITH_PRICE, PaymentMethod.CRYPTO);
        purchase.setStatus(PurchaseStatus.CONFIRMED);
        UUID id = UUID.randomUUID();
        setId(purchase, id);
        when(purchaseRepository.findById(id)).thenReturn(Optional.of(purchase));

        purchaseService.confirmPurchase(id);
        verifyNoInteractions(zenithLicenseRepository, plusService, referralService);
    }

    @Test
    void confirmPurchase_notPending_throws() {
        Purchase purchase = new Purchase(user, PlanType.ZENITH, 1, PlanPricing.ZENITH_PRICE, PaymentMethod.CRYPTO);
        purchase.setStatus(PurchaseStatus.REJECTED);
        UUID id = UUID.randomUUID();
        setId(purchase, id);
        when(purchaseRepository.findById(id)).thenReturn(Optional.of(purchase));
        assertThrows(BusinessRuleException.class, () -> purchaseService.confirmPurchase(id));
    }

    @Test
    void confirmPurchase_unknownId_throwsNotFound() {
        UUID id = UUID.randomUUID();
        when(purchaseRepository.findById(id)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> purchaseService.confirmPurchase(id));
    }

    @Test
    void confirmPurchase_zenith_createsLicense() {
        Purchase purchase = new Purchase(user, PlanType.ZENITH, 1, PlanPricing.ZENITH_PRICE, PaymentMethod.CRYPTO);
        UUID id = UUID.randomUUID();
        setId(purchase, id);
        when(purchaseRepository.findById(id)).thenReturn(Optional.of(purchase));
        when(invoiceService.generateStoreAndReturnBytes(any())).thenThrow(new RuntimeException("sin red"));

        purchaseService.confirmPurchase(id);
        verify(zenithLicenseRepository).save(any(ZenithLicense.class));
        verify(plusService, never()).createLicense(any(), any());
    }

    @Test
    void confirmPurchase_plus_createsLicenseViaPlusService() {
        Purchase purchase = new Purchase(user, PlanType.PLUS, 1, PlanPricing.PLUS_PRICE, PaymentMethod.ALTERNATIVE);
        UUID id = UUID.randomUUID();
        setId(purchase, id);
        when(purchaseRepository.findById(id)).thenReturn(Optional.of(purchase));
        when(invoiceService.generateStoreAndReturnBytes(any())).thenThrow(new RuntimeException("sin red"));

        purchaseService.confirmPurchase(id);
        verify(plusService).createLicense(user, purchase);
        verify(zenithLicenseRepository, never()).save(any());
    }

    // ---------- rejectPurchase() ----------

    @Test
    void rejectPurchase_pending_setsRejected() {
        Purchase purchase = new Purchase(user, PlanType.ZENITH, 1, PlanPricing.ZENITH_PRICE, PaymentMethod.ALTERNATIVE);
        UUID id = UUID.randomUUID();
        setId(purchase, id);
        when(purchaseRepository.findById(id)).thenReturn(Optional.of(purchase));

        Purchase result = purchaseService.rejectPurchase(id);
        assertThat(result.getStatus()).isEqualTo(PurchaseStatus.REJECTED);
        verify(referralService, never()).onReferredPurchaseConfirmed(any());
    }
}