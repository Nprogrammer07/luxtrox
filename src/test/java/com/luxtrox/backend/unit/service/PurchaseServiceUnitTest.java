package com.luxtrox.backend.unit.service;

import com.luxtrox.backend.entity.*;
import com.luxtrox.backend.entity.enums.PaymentMethod;
import com.luxtrox.backend.entity.enums.PlanType;
import com.luxtrox.backend.entity.enums.PurchaseStatus;
import com.luxtrox.backend.exception.BusinessRuleException;
import com.luxtrox.backend.exception.ResourceNotFoundException;
import com.luxtrox.backend.integration.nowpayments.NowPaymentsClient;
import com.luxtrox.backend.integration.nowpayments.dto.CreateInvoiceResponse;
import com.luxtrox.backend.repository.InvestmentPositionRepository;
import com.luxtrox.backend.repository.PurchaseRepository;
import com.luxtrox.backend.repository.UserRepository;
import com.luxtrox.backend.repository.ZenithLicenseRepository;
import com.luxtrox.backend.service.*;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
    @Mock private InvestmentPositionRepository positionRepository;
    @Mock private ZenithLicenseRepository zenithLicenseRepository;
    @Mock private ReferralService referralService;
    @Mock private AuditService auditService;
    @Mock private NowPaymentsClient nowPaymentsClient;
    @Mock private InvoiceService invoiceService;
    @Mock private NotificationEmailService notificationEmailService;
    @Mock private SystemConfigService systemConfigService;
    @Mock private PlusService plusService;

    private PurchaseService purchaseService;
    private MeterRegistry meterRegistry;
    private User user;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        purchaseService = new PurchaseService(purchaseRepository, userRepository, positionRepository,
                zenithLicenseRepository, referralService, auditService, nowPaymentsClient,
                invoiceService, notificationEmailService, meterRegistry, systemConfigService, plusService);

        Role role = new Role("USER", "Usuario estandar");
        user = new User("Carlos", "carlos@example.com", "+1", "hash", role, "CARLOS01");
        setId(user, UUID.randomUUID());
        user.setTotalPackagesPurchased(0);

        lenient().when(systemConfigService.getDriverPrice()).thenReturn(PlanPricing.DRIVER_PACKAGE_PRICE);
        lenient().when(systemConfigService.getMaxDriverPositions()).thenReturn(PlanPricing.MAX_DRIVER_PACKAGES);
        lenient().when(systemConfigService.getCashbackRate()).thenReturn(PlanPricing.CASHBACK_MULTIPLIER);
        lenient().when(systemConfigService.getMinWithdrawal()).thenReturn(new BigDecimal("50.00"));
        lenient().when(plusService.hasActiveLicense(any())).thenReturn(false);

        lenient().when(purchaseRepository.save(any(Purchase.class)))
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

    // ---------- createDriverPurchase() ----------

    @Test
    void createDriverPurchase_happyPath_computesCorrectTotal() {
        Purchase result = purchaseService.createDriverPurchase(user, 5, PaymentMethod.CRYPTO);

        assertThat(result.getPlanType()).isEqualTo(PlanType.DRIVER);
        assertThat(result.getPackageQuantity()).isEqualTo(5);
        assertThat(result.getTotalAmount()).isEqualByComparingTo(
                PlanPricing.DRIVER_PACKAGE_PRICE.multiply(BigDecimal.valueOf(5)));
    }

    @Test
    void createDriverPurchase_zeroQuantity_throws() {
        assertThrows(BusinessRuleException.class,
                () -> purchaseService.createDriverPurchase(user, 0, PaymentMethod.CRYPTO));
    }

    @Test
    void createDriverPurchase_aboveMax_throws() {
        assertThrows(BusinessRuleException.class,
                () -> purchaseService.createDriverPurchase(user, 31, PaymentMethod.CRYPTO));
    }

    @Test
    void createDriverPurchase_exactlyAtCumulativeCap_succeeds() {
        user.setTotalPackagesPurchased(25);
        Purchase result = purchaseService.createDriverPurchase(user, 5, PaymentMethod.CRYPTO);
        assertThat(result.getPackageQuantity()).isEqualTo(5);
    }

    @Test
    void createDriverPurchase_oneOverCumulativeCap_throws() {
        user.setTotalPackagesPurchased(26);
        assertThrows(BusinessRuleException.class,
                () -> purchaseService.createDriverPurchase(user, 5, PaymentMethod.CRYPTO));
    }

    // ---------- createZenithPurchase() ----------

    @Test
    void createZenithPurchase_withoutPlus_fullPrice() {
        when(plusService.hasActiveLicense(user)).thenReturn(false);

        Purchase result = purchaseService.createZenithPurchase(user, PaymentMethod.CRYPTO);

        assertThat(result.getPlanType()).isEqualTo(PlanType.ZENITH);
        assertThat(result.getPackageQuantity()).isEqualTo(1);
        assertThat(result.getTotalAmount()).isEqualByComparingTo(PlanPricing.ZENITH_PRICE);
    }

    @Test
    void createZenithPurchase_withActivePlus_appliesDiscount() {
        when(plusService.hasActiveLicense(user)).thenReturn(true);

        Purchase result = purchaseService.createZenithPurchase(user, PaymentMethod.CRYPTO);

        assertThat(result.getTotalAmount()).isEqualByComparingTo(
                PlanPricing.ZENITH_PRICE.subtract(PlanPricing.PLUS_ZENITH_DISCOUNT));
    }

    // ---------- createPlusPurchase() ----------

    @Test
    void createPlusPurchase_price200_planTypePlus() {
        Purchase result = purchaseService.createPlusPurchase(user, PaymentMethod.ALTERNATIVE);

        assertThat(result.getPlanType()).isEqualTo(PlanType.PLUS);
        assertThat(result.getPackageQuantity()).isEqualTo(1);
        assertThat(result.getTotalAmount()).isEqualByComparingTo(PlanPricing.PLUS_PRICE);
    }

    // ---------- initiateCryptoPayment() ----------

    @Test
    void initiateCryptoPayment_happyPath_returnsInvoiceUrlAndStoresId() {
        Purchase purchase = new Purchase(user, PlanType.DRIVER, 1, PlanPricing.DRIVER_PACKAGE_PRICE, PaymentMethod.CRYPTO);
        CreateInvoiceResponse fakeInvoice = new CreateInvoiceResponse(
                "inv-123", "https://nowpayments.io/pay/inv-123", "order-ref");
        when(nowPaymentsClient.createInvoice(purchase)).thenReturn(fakeInvoice);

        String url = purchaseService.initiateCryptoPayment(purchase);

        assertThat(url).isEqualTo("https://nowpayments.io/pay/inv-123");
        assertThat(purchase.getNowpaymentsInvoiceId()).isEqualTo("inv-123");
        verify(purchaseRepository).save(purchase);
    }

    @Test
    void initiateCryptoPayment_nonCryptoPurchase_throwsAndNeverCallsNowPayments() {
        Purchase purchase = new Purchase(user, PlanType.DRIVER, 1, PlanPricing.DRIVER_PACKAGE_PRICE, PaymentMethod.ALTERNATIVE);

        assertThrows(BusinessRuleException.class, () -> purchaseService.initiateCryptoPayment(purchase));
        verifyNoInteractions(nowPaymentsClient);
    }

    // ---------- confirmPurchase() ----------

    @Test
    void confirmPurchase_alreadyConfirmed_isIdempotentAndSkipsAllSideEffects() {
        Purchase purchase = new Purchase(user, PlanType.DRIVER, 1, PlanPricing.DRIVER_PACKAGE_PRICE, PaymentMethod.CRYPTO);
        purchase.setStatus(PurchaseStatus.CONFIRMED);
        UUID purchaseId = UUID.randomUUID();
        setId(purchase, purchaseId);
        when(purchaseRepository.findById(purchaseId)).thenReturn(Optional.of(purchase));

        Purchase result = purchaseService.confirmPurchase(purchaseId);

        assertThat(result).isSameAs(purchase);
        verifyNoInteractions(positionRepository, zenithLicenseRepository, referralService, invoiceService, plusService);
    }

    @Test
    void confirmPurchase_notPending_throws() {
        Purchase purchase = new Purchase(user, PlanType.DRIVER, 1, PlanPricing.DRIVER_PACKAGE_PRICE, PaymentMethod.CRYPTO);
        purchase.setStatus(PurchaseStatus.REJECTED);
        UUID purchaseId = UUID.randomUUID();
        setId(purchase, purchaseId);
        when(purchaseRepository.findById(purchaseId)).thenReturn(Optional.of(purchase));

        assertThrows(BusinessRuleException.class, () -> purchaseService.confirmPurchase(purchaseId));
    }

    @Test
    void confirmPurchase_unknownId_throwsResourceNotFound() {
        UUID purchaseId = UUID.randomUUID();
        when(purchaseRepository.findById(purchaseId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> purchaseService.confirmPurchase(purchaseId));
    }

    @Test
    void confirmPurchase_driver_createsPositionWithTripleTargetAndUpdatesPackageCounter() {
        Purchase purchase = new Purchase(user, PlanType.DRIVER, 3,
                PlanPricing.DRIVER_PACKAGE_PRICE.multiply(BigDecimal.valueOf(3)), PaymentMethod.CRYPTO);
        UUID purchaseId = UUID.randomUUID();
        setId(purchase, purchaseId);
        user.setTotalPackagesPurchased(2);
        when(purchaseRepository.findById(purchaseId)).thenReturn(Optional.of(purchase));
        when(invoiceService.generateStoreAndReturnBytes(any())).thenThrow(new RuntimeException("sin red"));

        purchaseService.confirmPurchase(purchaseId);

        ArgumentCaptor<InvestmentPosition> positionCaptor = ArgumentCaptor.forClass(InvestmentPosition.class);
        verify(positionRepository).save(positionCaptor.capture());
        assertThat(positionCaptor.getValue().getTargetCashback())
                .isEqualByComparingTo(purchase.getTotalAmount().multiply(PlanPricing.CASHBACK_MULTIPLIER));
        assertThat(user.getTotalPackagesPurchased()).isEqualTo(5);
        verify(zenithLicenseRepository, never()).save(any());
        verify(referralService).onReferredPurchaseConfirmed(purchase);
    }

    @Test
    void confirmPurchase_zenith_createsLicenseAndNeverTouchesPackageCounter() {
        Purchase purchase = new Purchase(user, PlanType.ZENITH, 1, PlanPricing.ZENITH_PRICE, PaymentMethod.CRYPTO);
        UUID purchaseId = UUID.randomUUID();
        setId(purchase, purchaseId);
        when(purchaseRepository.findById(purchaseId)).thenReturn(Optional.of(purchase));
        when(invoiceService.generateStoreAndReturnBytes(any())).thenThrow(new RuntimeException("sin red"));

        purchaseService.confirmPurchase(purchaseId);

        verify(zenithLicenseRepository).save(any(ZenithLicense.class));
        verify(positionRepository, never()).save(any());
        verify(userRepository, never()).save(any());
        verify(plusService, never()).createLicense(any(), any());
    }

    @Test
    void confirmPurchase_plus_createsLicenseViaPlusService() {
        Purchase purchase = new Purchase(user, PlanType.PLUS, 1, PlanPricing.PLUS_PRICE, PaymentMethod.ALTERNATIVE);
        UUID purchaseId = UUID.randomUUID();
        setId(purchase, purchaseId);
        when(purchaseRepository.findById(purchaseId)).thenReturn(Optional.of(purchase));
        when(invoiceService.generateStoreAndReturnBytes(any())).thenThrow(new RuntimeException("sin red"));

        purchaseService.confirmPurchase(purchaseId);

        verify(plusService).createLicense(user, purchase);
        verify(positionRepository, never()).save(any());
        verify(zenithLicenseRepository, never()).save(any());
        verify(referralService).onReferredPurchaseConfirmed(purchase);
    }

    @Test
    void confirmPurchase_invoiceOrEmailFailure_stillConfirmsAndAudits() {
        Purchase purchase = new Purchase(user, PlanType.DRIVER, 1, PlanPricing.DRIVER_PACKAGE_PRICE, PaymentMethod.CRYPTO);
        UUID purchaseId = UUID.randomUUID();
        setId(purchase, purchaseId);
        when(purchaseRepository.findById(purchaseId)).thenReturn(Optional.of(purchase));
        when(invoiceService.generateStoreAndReturnBytes(any())).thenThrow(new RuntimeException("Storage caido"));

        Purchase result = purchaseService.confirmPurchase(purchaseId);

        assertThat(result.getStatus()).isEqualTo(PurchaseStatus.CONFIRMED);
        verify(auditService).recordSystemAction(eq("Purchase"), any(), eq("INVOICE_OR_EMAIL_FAILED"), any(), any());
        verify(notificationEmailService, never()).sendPurchaseConfirmedEmail(any(), any(), any());
    }

    // ---------- rejectPurchase() ----------

    @Test
    void rejectPurchase_pending_setsRejectedAndAudits() {
        Purchase purchase = new Purchase(user, PlanType.DRIVER, 1, PlanPricing.DRIVER_PACKAGE_PRICE, PaymentMethod.ALTERNATIVE);
        UUID purchaseId = UUID.randomUUID();
        setId(purchase, purchaseId);
        when(purchaseRepository.findById(purchaseId)).thenReturn(Optional.of(purchase));

        Purchase result = purchaseService.rejectPurchase(purchaseId);

        assertThat(result.getStatus()).isEqualTo(PurchaseStatus.REJECTED);
        verify(purchaseRepository).save(purchase);
        verify(auditService).record(user, "Purchase", purchaseId, "PURCHASE_REJECTED",
                PurchaseStatus.PENDING, PurchaseStatus.REJECTED);
        verify(positionRepository, never()).save(any());
        verify(zenithLicenseRepository, never()).save(any());
        verify(referralService, never()).onReferredPurchaseConfirmed(any());
    }

    @Test
    void rejectPurchase_alreadyConfirmed_throwsAndNeverSaves() {
        Purchase purchase = new Purchase(user, PlanType.DRIVER, 1, PlanPricing.DRIVER_PACKAGE_PRICE, PaymentMethod.ALTERNATIVE);
        purchase.setStatus(PurchaseStatus.CONFIRMED);
        UUID purchaseId = UUID.randomUUID();
        setId(purchase, purchaseId);
        when(purchaseRepository.findById(purchaseId)).thenReturn(Optional.of(purchase));

        assertThrows(BusinessRuleException.class, () -> purchaseService.rejectPurchase(purchaseId));
        verify(purchaseRepository, never()).save(any());
    }

    @Test
    void rejectPurchase_unknownId_throwsResourceNotFound() {
        UUID purchaseId = UUID.randomUUID();
        when(purchaseRepository.findById(purchaseId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> purchaseService.rejectPurchase(purchaseId));
    }

    // ---------- listAllPurchases() ----------

    @Test
    void listAllPurchases_mapsUserFieldsCorrectly() {
        Purchase purchase = new Purchase(user, PlanType.ZENITH, 1, PlanPricing.ZENITH_PRICE, PaymentMethod.ALTERNATIVE);
        setId(purchase, UUID.randomUUID());
        when(purchaseRepository.findAll()).thenReturn(java.util.List.of(purchase));

        var result = purchaseService.listAllPurchases(null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).userId()).isEqualTo(user.getId());
        assertThat(result.get(0).userName()).isEqualTo("Carlos");
        assertThat(result.get(0).userEmail()).isEqualTo("carlos@example.com");
        assertThat(result.get(0).planType()).isEqualTo(PlanType.ZENITH);
        assertThat(result.get(0).status()).isEqualTo(PurchaseStatus.PENDING);
    }

    @Test
    void listAllPurchases_withStatusFilter_delegatesToFindByStatus_notFindAll() {
        when(purchaseRepository.findByStatus(PurchaseStatus.PENDING)).thenReturn(java.util.List.of());

        purchaseService.listAllPurchases(PurchaseStatus.PENDING);

        verify(purchaseRepository).findByStatus(PurchaseStatus.PENDING);
        verify(purchaseRepository, never()).findAll();
    }
}