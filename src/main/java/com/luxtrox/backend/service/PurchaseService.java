package com.luxtrox.backend.service;

import com.luxtrox.backend.entity.*;
import com.luxtrox.backend.entity.enums.PaymentMethod;
import com.luxtrox.backend.entity.enums.PlanType;
import com.luxtrox.backend.entity.enums.PurchaseStatus;
import com.luxtrox.backend.dto.purchase.AdminPurchaseResponse;
import com.luxtrox.backend.exception.BusinessRuleException;
import com.luxtrox.backend.exception.ResourceNotFoundException;
import com.luxtrox.backend.integration.nowpayments.NowPaymentsClient;
import com.luxtrox.backend.integration.nowpayments.dto.CreateInvoiceResponse;
import com.luxtrox.backend.repository.PurchaseRepository;
import com.luxtrox.backend.repository.UserRepository;
import com.luxtrox.backend.repository.ZenithLicenseRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class PurchaseService {

    private final PurchaseRepository purchaseRepository;
    private final UserRepository userRepository;
    private final ZenithLicenseRepository zenithLicenseRepository;
    private final ReferralService referralService;
    private final AuditService auditService;
    private final NowPaymentsClient nowPaymentsClient;
    private final InvoiceService invoiceService;
    private final NotificationEmailService notificationEmailService;
    private final MeterRegistry meterRegistry;
    private final PlusService plusService;

    public PurchaseService(PurchaseRepository purchaseRepository,
                            UserRepository userRepository,
                            ZenithLicenseRepository zenithLicenseRepository,
                            ReferralService referralService,
                            AuditService auditService,
                            NowPaymentsClient nowPaymentsClient,
                            InvoiceService invoiceService,
                            NotificationEmailService notificationEmailService,
                            MeterRegistry meterRegistry,
                            PlusService plusService) {
        this.purchaseRepository = purchaseRepository;
        this.userRepository = userRepository;
        this.zenithLicenseRepository = zenithLicenseRepository;
        this.referralService = referralService;
        this.auditService = auditService;
        this.nowPaymentsClient = nowPaymentsClient;
        this.invoiceService = invoiceService;
        this.notificationEmailService = notificationEmailService;
        this.meterRegistry = meterRegistry;
        this.plusService = plusService;
    }

    @Transactional
    public Purchase createZenithPurchase(User user, PaymentMethod paymentMethod) {
        BigDecimal zenithPrice = PlanPricing.ZENITH_PRICE;
        if (plusService.hasActiveLicense(user)) {
            zenithPrice = zenithPrice.subtract(PlanPricing.PLUS_ZENITH_DISCOUNT);
        }
        Purchase purchase = new Purchase(user, PlanType.ZENITH, 1, zenithPrice, paymentMethod);
        return purchaseRepository.save(purchase);
    }

    @Transactional
    public Purchase createPlusPurchase(User user, PaymentMethod paymentMethod) {
        Purchase purchase = new Purchase(user, PlanType.PLUS, 1, PlanPricing.PLUS_PRICE, paymentMethod);
        return purchaseRepository.save(purchase);
    }

    @Transactional
    public String initiateCryptoPayment(Purchase purchase) {
        if (purchase.getPaymentMethod() != PaymentMethod.CRYPTO) {
            throw new BusinessRuleException("initiateCryptoPayment solo aplica a compras CRYPTO");
        }
        CreateInvoiceResponse invoice = nowPaymentsClient.createInvoice(purchase);
        purchase.setNowpaymentsInvoiceId(invoice.id());
        purchaseRepository.save(purchase);
        return invoice.invoiceUrl();
    }

    @Transactional
    public Purchase confirmPurchase(UUID purchaseId) {
        Purchase purchase = purchaseRepository.findById(purchaseId)
                .orElseThrow(() -> new ResourceNotFoundException("Compra no encontrada"));

        if (purchase.getStatus() == PurchaseStatus.CONFIRMED) return purchase;
        if (purchase.getStatus() != PurchaseStatus.PENDING) {
            throw new BusinessRuleException(
                    "Solo se puede confirmar una compra PENDING (estado: " + purchase.getStatus() + ")");
        }

        purchase.setStatus(PurchaseStatus.CONFIRMED);
        purchase.setConfirmedAt(OffsetDateTime.now());

        User user = purchase.getUser();

        if (purchase.getPlanType() == PlanType.ZENITH) {
            OffsetDateTime now = OffsetDateTime.now();
            ZenithLicense license = new ZenithLicense(user, purchase, now, now.plusYears(1));
            zenithLicenseRepository.save(license);
        } else {
            // PLUS
            plusService.createLicense(user, purchase);
        }

        purchaseRepository.save(purchase);
        auditService.record(user, "Purchase", purchase.getId(), "PURCHASE_CONFIRMED",
                PurchaseStatus.PENDING, PurchaseStatus.CONFIRMED);
        Counter.builder("luxtrox.purchases.confirmed")
                .tag("planType", purchase.getPlanType().name())
                .register(meterRegistry).increment();

        generateInvoiceAndNotify(purchase);
        referralService.onReferredPurchaseConfirmed(purchase);
        return purchase;
    }

    private void generateInvoiceAndNotify(Purchase purchase) {
        try {
            InvoiceService.InvoiceWithBytes result = invoiceService.generateStoreAndReturnBytes(purchase);
            notificationEmailService.sendPurchaseConfirmedEmail(
                    purchase, result.pdfBytes(), result.invoice().getInvoiceNumber());
        } catch (Exception e) {
            auditService.recordSystemAction("Purchase", purchase.getId(),
                    "INVOICE_OR_EMAIL_FAILED", null, e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public List<AdminPurchaseResponse> listAllPurchases(PurchaseStatus status) {
        List<Purchase> purchases = status != null
                ? purchaseRepository.findByStatus(status)
                : purchaseRepository.findAll();
        return purchases.stream().map(this::toAdminPurchaseResponse).toList();
    }

    private AdminPurchaseResponse toAdminPurchaseResponse(Purchase purchase) {
        User user = purchase.getUser();
        return new AdminPurchaseResponse(
                purchase.getId(), user.getId(), user.getFullName(), user.getEmail(),
                purchase.getPlanType(), purchase.getPackageQuantity(), purchase.getTotalAmount(),
                purchase.getPaymentMethod(), purchase.getStatus(), purchase.getCreatedAt(),
                purchase.getConfirmedAt());
    }

    @Transactional
    public Purchase rejectPurchase(UUID purchaseId) {
        Purchase purchase = purchaseRepository.findById(purchaseId)
                .orElseThrow(() -> new ResourceNotFoundException("Compra no encontrada"));
        if (purchase.getStatus() != PurchaseStatus.PENDING) {
            throw new BusinessRuleException(
                    "Solo se puede rechazar una compra PENDING (estado: " + purchase.getStatus() + ")");
        }
        purchase.setStatus(PurchaseStatus.REJECTED);
        purchaseRepository.save(purchase);
        auditService.record(purchase.getUser(), "Purchase", purchase.getId(),
                "PURCHASE_REJECTED", PurchaseStatus.PENDING, PurchaseStatus.REJECTED);
        return purchase;
    }
}
