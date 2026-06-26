package com.luxtrox.backend.service;

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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Crea y confirma compras de los dos planes (ver docs/domain-model.md
 * 7.1). DRIVER genera una InvestmentPosition (participa del motor de
 * cashback); ZENITH genera una ZenithLicense (no participa de cashback
 * en absoluto, solo requiere renovacion anual -- ver ZenithService).
 *
 * Fase 7: las compras CRYPTO generan un invoice en NOWPayments
 * (initiateCryptoPayment); al confirmarse CUALQUIER compra (sin
 * importar el metodo de pago) se genera la factura PDF y se envia el
 * correo de confirmacion.
 */
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

    public PurchaseService(PurchaseRepository purchaseRepository,
                            UserRepository userRepository,
                            InvestmentPositionRepository positionRepository,
                            ZenithLicenseRepository zenithLicenseRepository,
                            ReferralService referralService,
                            AuditService auditService,
                            NowPaymentsClient nowPaymentsClient,
                            InvoiceService invoiceService,
                            NotificationEmailService notificationEmailService) {
        this.purchaseRepository = purchaseRepository;
        this.userRepository = userRepository;
        this.positionRepository = positionRepository;
        this.zenithLicenseRepository = zenithLicenseRepository;
        this.referralService = referralService;
        this.auditService = auditService;
        this.nowPaymentsClient = nowPaymentsClient;
        this.invoiceService = invoiceService;
        this.notificationEmailService = notificationEmailService;
    }

    @Transactional
    public Purchase createDriverPurchase(User user, Integer packageQuantity, PaymentMethod paymentMethod) {
        if (packageQuantity == null || packageQuantity < 1 || packageQuantity > PlanPricing.MAX_DRIVER_PACKAGES) {
            throw new BusinessRuleException(
                    "La cantidad de paquetes debe estar entre 1 y " + PlanPricing.MAX_DRIVER_PACKAGES);
        }
        // Tope ACUMULADO por usuario, no por compra individual (ver
        // docs/domain-model.md supuesto #1, confirmado por el cliente).
        int totalAfter = user.getTotalPackagesPurchased() + packageQuantity;
        if (totalAfter > PlanPricing.MAX_DRIVER_PACKAGES) {
            throw new BusinessRuleException(
                    "Esta compra superaria el tope de " + PlanPricing.MAX_DRIVER_PACKAGES
                            + " paquetes acumulados (ya tiene " + user.getTotalPackagesPurchased() + ")");
        }

        BigDecimal totalAmount = PlanPricing.DRIVER_PACKAGE_PRICE
                .multiply(BigDecimal.valueOf(packageQuantity));

        Purchase purchase = new Purchase(user, PlanType.DRIVER, packageQuantity, totalAmount, paymentMethod);
        return purchaseRepository.save(purchase);
    }

    @Transactional
    public Purchase createZenithPurchase(User user, PaymentMethod paymentMethod) {
        Purchase purchase = new Purchase(user, PlanType.ZENITH, 1, PlanPricing.ZENITH_PRICE, paymentMethod);
        return purchaseRepository.save(purchase);
    }

    /**
     * Solo aplica a compras con paymentMethod = CRYPTO. Crea el
     * invoice en NOWPayments, guarda su id en la compra, y devuelve la
     * URL a la que el frontend debe redirigir al usuario para pagar.
     * La confirmacion real llega despues, de forma asincrona, via
     * NowPaymentsWebhookController -- esta llamada NUNCA confirma la
     * compra por si misma.
     */
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

    /**
     * Confirma una compra ya pagada: crea la posicion (DRIVER) o la
     * licencia (ZENITH), actualiza contadores del usuario, genera la
     * factura PDF + correo de confirmacion, y dispara la evaluacion de
     * comisiones de referido en ambas direcciones (el comprador como
     * referido, y el comprador como referente de otros que estaban
     * esperando -- ver docs/domain-model.md 7.2).
     */
    @Transactional
    public Purchase confirmPurchase(UUID purchaseId) {
        Purchase purchase = purchaseRepository.findById(purchaseId)
                .orElseThrow(() -> new ResourceNotFoundException("Compra no encontrada"));

        if (purchase.getStatus() == PurchaseStatus.CONFIRMED) {
            return purchase; // idempotente -- ya confirmada, no repetir efectos
        }
        if (purchase.getStatus() != PurchaseStatus.PENDING) {
            throw new BusinessRuleException(
                    "Solo se puede confirmar una compra PENDING (estado actual: " + purchase.getStatus() + ")");
        }

        purchase.setStatus(PurchaseStatus.CONFIRMED);
        purchase.setConfirmedAt(OffsetDateTime.now());

        User user = purchase.getUser();

        if (purchase.getPlanType() == PlanType.DRIVER) {
            BigDecimal targetCashback = purchase.getTotalAmount().multiply(PlanPricing.CASHBACK_MULTIPLIER);
            InvestmentPosition position = new InvestmentPosition(user, purchase, purchase.getTotalAmount(), targetCashback);
            positionRepository.save(position);
            purchase.setPosition(position);

            user.setTotalPackagesPurchased(user.getTotalPackagesPurchased() + purchase.getPackageQuantity());
            userRepository.save(user);
        } else {
            OffsetDateTime now = OffsetDateTime.now();
            ZenithLicense license = new ZenithLicense(user, purchase, now, now.plusYears(1));
            zenithLicenseRepository.save(license);
        }

        purchaseRepository.save(purchase);

        auditService.record(user, "Purchase", purchase.getId(), "PURCHASE_CONFIRMED",
                PurchaseStatus.PENDING, PurchaseStatus.CONFIRMED);

        generateInvoiceAndNotify(purchase);

        // El comprador puede ser un REFERIDO de alguien -- evalua y
        // paga esa comision si corresponde.
        referralService.onReferredPurchaseConfirmed(purchase);

        // Esta puede ser la primera compra confirmada del comprador
        // -- si el mismo tiene referidos pendientes esperando a que
        // el calificara, esto los re-evalua y paga.
        referralService.onReferrerPurchaseConfirmed(user);

        return purchase;
    }

    /**
     * Aislado en su propio metodo para que un fallo de email/PDF
     * (ej. Resend caido) NUNCA tumbe la confirmacion de la compra en
     * si -- el dinero/posicion/licencia ya quedaron correctos antes de
     * llegar aqui. Se atrapa cualquier excepcion y solo se deja
     * constancia en el log, no se relanza.
     */
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
}
