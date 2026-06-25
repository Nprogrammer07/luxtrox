package com.luxtrox.backend.service;

import com.luxtrox.backend.entity.ZenithLicense;
import com.luxtrox.backend.entity.ZenithRenewalPayment;
import com.luxtrox.backend.entity.enums.ZenithLicenseStatus;
import com.luxtrox.backend.exception.ResourceNotFoundException;
import com.luxtrox.backend.repository.ZenithLicenseRepository;
import com.luxtrox.backend.repository.ZenithRenewalPaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Renovacion anual de licencias Zenith -- $250 fijos, no participa
 * del motor de cashback en absoluto (ver docs/domain-model.md 7.1).
 */
@Service
public class ZenithService {

    private final ZenithLicenseRepository licenseRepository;
    private final ZenithRenewalPaymentRepository renewalRepository;
    private final AuditService auditService;

    public ZenithService(ZenithLicenseRepository licenseRepository,
                          ZenithRenewalPaymentRepository renewalRepository,
                          AuditService auditService) {
        this.licenseRepository = licenseRepository;
        this.renewalRepository = renewalRepository;
        this.auditService = auditService;
    }

    @Transactional
    public ZenithRenewalPayment renew(UUID licenseId) {
        ZenithLicense license = licenseRepository.findById(licenseId)
                .orElseThrow(() -> new ResourceNotFoundException("Licencia Zenith no encontrada"));

        OffsetDateTime periodStart = license.getCurrentPeriodEnd();
        OffsetDateTime periodEnd = periodStart.plusYears(1);

        ZenithRenewalPayment payment = renewalRepository.save(
                new ZenithRenewalPayment(license, periodStart, periodEnd));

        ZenithLicenseStatus oldStatus = license.getStatus();
        license.setCurrentPeriodEnd(periodEnd);
        license.setStatus(ZenithLicenseStatus.ACTIVE);
        licenseRepository.save(license);

        auditService.record(license.getUser(), "ZenithLicense", license.getId(), "RENEWED",
                oldStatus, license.getStatus());

        return payment;
    }

    /**
     * Marca como EXPIRED cualquier licencia cuyo periodo ya termino y
     * no se renovo a tiempo. Pensado para correr periodicamente (cron
     * -- la infraestructura de jobs programados es de una fase
     * posterior, esto solo expone el metodo que ese job invocaria).
     */
    @Transactional
    public int expireOverdueLicenses() {
        var activeLicenses = licenseRepository.findByStatus(ZenithLicenseStatus.ACTIVE);
        int expiredCount = 0;

        for (ZenithLicense license : activeLicenses) {
            if (license.getCurrentPeriodEnd().isBefore(OffsetDateTime.now())) {
                license.setStatus(ZenithLicenseStatus.EXPIRED);
                licenseRepository.save(license);
                auditService.recordSystemAction("ZenithLicense", license.getId(), "EXPIRED",
                        ZenithLicenseStatus.ACTIVE, ZenithLicenseStatus.EXPIRED);
                expiredCount++;
            }
        }
        return expiredCount;
    }
}
