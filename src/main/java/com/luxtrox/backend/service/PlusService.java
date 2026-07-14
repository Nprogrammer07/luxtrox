package com.luxtrox.backend.service;

import com.luxtrox.backend.entity.PlusLicense;
import com.luxtrox.backend.entity.PlusRenewalPayment;
import com.luxtrox.backend.entity.Purchase;
import com.luxtrox.backend.entity.User;
import com.luxtrox.backend.entity.enums.PlanType;
import com.luxtrox.backend.entity.enums.PlusLicenseStatus;
import com.luxtrox.backend.exception.ResourceNotFoundException;
import com.luxtrox.backend.repository.PlusLicenseRepository;
import com.luxtrox.backend.repository.PlusRenewalPaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Luxtrox Genius (interno: PLUS) -- matrícula ANUAL de $200.
 *
 * Modelo espejo del de Zenith: la licencia dura 1 año; al renovar se
 * extiende 1 año más y se registra un PlusRenewalPayment. A diferencia
 * de Zenith, CADA renovación paga comisión de referido ($50 = 25% de
 * $200) al referente del alumno -- por eso este servicio inyecta
 * ReferralService, que ZenithService no hace.
 */
@Service
public class PlusService {

    private final PlusLicenseRepository plusLicenseRepository;
    private final PlusRenewalPaymentRepository renewalRepository;
    private final ReferralService referralService;
    private final AuditService auditService;

    public PlusService(PlusLicenseRepository plusLicenseRepository,
                        PlusRenewalPaymentRepository renewalRepository,
                        ReferralService referralService,
                        AuditService auditService) {
        this.plusLicenseRepository = plusLicenseRepository;
        this.renewalRepository = renewalRepository;
        this.referralService = referralService;
        this.auditService = auditService;
    }

    /** Crea la licencia cuando una compra Genius se confirma (1 año de acceso). */
    @Transactional
    public PlusLicense createLicense(User user, Purchase purchase) {
        PlusLicense license = new PlusLicense(user, purchase);
        return plusLicenseRepository.save(license);
    }

    /**
     * Renueva la matrícula un año más ($200). Registra el pago, extiende
     * el periodo, reactiva la licencia si estaba EXPIRED, y paga la
     * comisión de referido correspondiente.
     */
    @Transactional
    public PlusRenewalPayment renew(UUID licenseId) {
        PlusLicense license = plusLicenseRepository.findById(licenseId)
                .orElseThrow(() -> new ResourceNotFoundException("Licencia Genius no encontrada"));

        OffsetDateTime periodStart = license.getCurrentPeriodEnd();
        OffsetDateTime periodEnd = periodStart.plusYears(1);

        PlusRenewalPayment payment = renewalRepository.save(
                new PlusRenewalPayment(license, periodStart, periodEnd));

        PlusLicenseStatus oldStatus = license.getStatus();
        license.setCurrentPeriodEnd(periodEnd);
        license.setStatus(PlusLicenseStatus.ACTIVE);
        plusLicenseRepository.save(license);

        auditService.record(license.getUser(), "PlusLicense", license.getId(), "RENEWED",
                oldStatus, license.getStatus());

        // Cada renovación anual paga comisión al referente del alumno.
        referralService.onRenewalConfirmed(
                license.getUser(), PlanType.PLUS, PlusRenewalPayment.RENEWAL_AMOUNT);

        return payment;
    }

    /**
     * Marca como EXPIRED las licencias cuyo periodo ya venció sin renovar.
     * El alumno pierde acceso a los recursos académicos (y el descuento
     * en Zenith, porque hasActiveLicense() deja de devolver true).
     */
    @Transactional
    public int expireOverdueLicenses() {
        List<PlusLicense> activeLicenses = plusLicenseRepository.findByStatus(PlusLicenseStatus.ACTIVE);
        int expiredCount = 0;

        for (PlusLicense license : activeLicenses) {
            if (license.getCurrentPeriodEnd().isBefore(OffsetDateTime.now())) {
                license.setStatus(PlusLicenseStatus.EXPIRED);
                plusLicenseRepository.save(license);
                auditService.recordSystemAction("PlusLicense", license.getId(), "EXPIRED",
                        PlusLicenseStatus.ACTIVE, PlusLicenseStatus.EXPIRED);
                expiredCount++;
            }
        }
        return expiredCount;
    }

    @Transactional(readOnly = true)
    public List<PlusLicense> getLicensesForUser(User user) {
        return plusLicenseRepository.findByUserOrderByPurchasedAtDesc(user);
    }

    /**
     * ¿Tiene matrícula Genius ACTIVA? Se usa para el descuento de $100
     * al comprar Zenith -- si la matrícula expiró, pierde el descuento.
     */
    @Transactional(readOnly = true)
    public boolean hasActiveLicense(User user) {
        return plusLicenseRepository.existsByUserAndStatus(user, PlusLicenseStatus.ACTIVE);
    }
}
