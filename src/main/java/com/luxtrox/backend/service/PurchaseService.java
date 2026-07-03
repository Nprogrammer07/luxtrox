package com.luxtrox.backend.service;

import com.luxtrox.backend.entity.*;
import com.luxtrox.backend.entity.enums.PaymentMethod;
import com.luxtrox.backend.entity.enums.PlanType;
import com.luxtrox.backend.entity.enums.PurchaseStatus;
import com.luxtrox.backend.dto.purchase.AdminPurchaseResponse;
import com.luxtrox.backend.dto.purchase.AdminSeminarResponse;
import com.luxtrox.backend.exception.BusinessRuleException;
import com.luxtrox.backend.exception.ResourceNotFoundException;
import com.luxtrox.backend.integration.nowpayments.NowPaymentsClient;
import com.luxtrox.backend.integration.nowpayments.dto.CreateInvoiceResponse;
import com.luxtrox.backend.repository.InvestmentPositionRepository;
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
    private final InvestmentPositionRepository positionRepository;
    private final ZenithLicenseRepository zenithLicenseRepository;
    private final ReferralService referralService;
    private final AuditService auditService;
    private final NowPaymentsClient nowPaymentsClient;
    private final InvoiceService invoiceService;
    private final NotificationEmailService notificationEmailService;
    private final MeterRegistry meterRegistry;
    private final SystemConfigService systemConfigService;
    private final PlusService plusService;

    public PurchaseService(PurchaseRepository purchaseRepository,
                            UserRepository userRepository,
                            InvestmentPositionRepository positionRepository,
                            ZenithLicenseRepository zenithLicenseRepository,
                            ReferralService referralService,
                            AuditService auditService,
                            NowPaymentsClient nowPaymentsClient,
                            InvoiceService invoiceService,
                            NotificationEmailService notificationEmailService,
                            MeterRegistry meterRegistry,
                            SystemConfigService systemConfigService,
                            PlusService plusService) {
        this.purchaseRepository = purchaseRepository;
        this.userRepository = userRepository;
        this.positionRepository = positionRepository;
        this.zenithLicenseRepository = zenithLicenseRepository;
        this.referralService = referralService;
        this.auditService = auditService;
        this.nowPaymentsClient = nowPaymentsClient;
        this.invoiceService = invoiceService;
        this.notificationEmailService = notificationEmailService;
        this.meterRegistry = meterRegistry;
        this.systemConfigService = systemConfigService;
        this.plusService = plusService;
    }

    @Transactional
    public Purchase createDriverPurchase(User user, Integer packageQuantity, PaymentMethod paymentMethod) {
        int maxPositions = systemConfigService.getMaxDriverPositions();
        if (packageQuantity == null || packageQuantity < 1 || packageQuantity > maxPositions) {
            throw new BusinessRuleException(
                    "La cantidad de paquetes debe estar entre 1 y " + maxPositions);
        }
        int totalAfter = user.getTotalPackagesPurchased() + packageQuantity;
        if (totalAfter > maxPositions) {
            throw new BusinessRuleException(
                    "Esta compra superaria el tope de " + maxPositions
                            + " paquetes acumulados (ya tiene " + user.getTotalPackagesPurchased() + ")");
        }

        BigDecimal unitPrice = systemConfigService.getDriverPrice();
        BigDecimal totalAmount = unitPrice.multiply(BigDecimal.valueOf(packageQuantity));

        Purchase purchase = new Purchase(user, PlanType.DRIVER, packageQuantity, totalAmount, paymentMethod);
        return purchaseRepository.save(purchase);
    }

    @Transactional
    public Purchase createZenithPurchase(User user, PaymentMethod paymentMethod) {
        // $100 de descuento si el usuario tiene Luxtrox Plus activo
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

        if (purchase.getStatus() == PurchaseStatus.CONFIRMED) {
            return purchase;
        }
        if (purchase.getStatus() != PurchaseStatus.PENDING) {
            throw new BusinessRuleException(
                    "Solo se puede confirmar una compra PENDING (estado actual: " + purchase.getStatus() + ")");
        }

        purchase.setStatus(PurchaseStatus.CONFIRMED);
        purchase.setConfirmedAt(OffsetDateTime.now());

        User user = purchase.getUser();

        if (purchase.getPlanType() == PlanType.DRIVER) {
            BigDecimal targetCashback = purchase.getTotalAmount().multiply(systemConfigService.getCashbackRate());
            InvestmentPosition position = new InvestmentPosition(user, purchase, purchase.getTotalAmount(), targetCashback);
            positionRepository.save(position);
            purchase.setPosition(position);

            user.setTotalPackagesPurchased(user.getTotalPackagesPurchased() + purchase.getPackageQuantity());
            userRepository.save(user);

        } else if (purchase.getPlanType() == PlanType.ZENITH) {
            OffsetDateTime now = OffsetDateTime.now();
            ZenithLicense license = new ZenithLicense(user, purchase, now, now.plusYears(1));
            zenithLicenseRepository.save(license);

        } else {
            // PLUS — licencia educativa de 5 años
            plusService.createLicense(user, purchase);
        }

        purchaseRepository.save(purchase);

        auditService.record(user, "Purchase", purchase.getId(), "PURCHASE_CONFIRMED",
                PurchaseStatus.PENDING, PurchaseStatus.CONFIRMED);

        Counter.builder("luxtrox.purchases.confirmed")
                .description("Compras confirmadas, por tipo de plan")
                .tag("planType", purchase.getPlanType().name())
                .register(meterRegistry)
                .increment();

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
            auditService.recordSystemAction("Purchase", purchase.getId(), "INVOICE_OR_EMAIL_FAILED",
                    null, e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public List<AdminSeminarResponse> listAllSeminars() {
        return positionRepository.findAll().stream()
                .map(this::toSeminarResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AdminSeminarResponse> listMySeminars(User user) {
        return positionRepository.findByUser(user).stream()
                .map(this::toSeminarResponse)
                .toList();
    }

    private AdminSeminarResponse toSeminarResponse(InvestmentPosition p) {
        User user = p.getUser();
        return new AdminSeminarResponse(
                p.getId(), user.getId(), user.getFullName(), user.getEmail(),
                p.getCapital(), p.getTargetCashback(),
                p.getCashbackPaid(), p.getStatus().name().toLowerCase(),
                p.getCreatedAt(), p.getCreatedAt(), p.getCompletedAt());
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
                    "Solo se puede rechazar una compra PENDING (estado actual: " + purchase.getStatus() + ")");
        }

        purchase.setStatus(PurchaseStatus.REJECTED);
        purchaseRepository.save(purchase);

        auditService.record(purchase.getUser(), "Purchase", purchase.getId(), "PURCHASE_REJECTED",
                PurchaseStatus.PENDING, PurchaseStatus.REJECTED);

        return purchase;
    }
}
