package com.luxtrox.backend.unit.service;

import com.luxtrox.backend.entity.*;
import com.luxtrox.backend.entity.enums.PaymentMethod;
import com.luxtrox.backend.entity.enums.PlanType;
import com.luxtrox.backend.entity.enums.PurchaseStatus;
import com.luxtrox.backend.entity.enums.ZenithLicenseStatus;
import com.luxtrox.backend.exception.ResourceNotFoundException;
import com.luxtrox.backend.repository.ZenithLicenseRepository;
import com.luxtrox.backend.repository.ZenithRenewalPaymentRepository;
import com.luxtrox.backend.service.AuditService;
import com.luxtrox.backend.service.ZenithService;
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
 * Unitario puro con Mockito -- complementa a ZenithServiceTest
 * (Testcontainers, Fase 6).
 */
@ExtendWith(MockitoExtension.class)
class ZenithServiceUnitTest {

    @Mock private ZenithLicenseRepository licenseRepository;
    @Mock private ZenithRenewalPaymentRepository renewalRepository;
    @Mock private AuditService auditService;

    private ZenithService service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new ZenithService(licenseRepository, renewalRepository, auditService);

        Role role = new Role("USER", "Usuario estandar");
        user = new User("Carlos", "carlos@example.com", "+1", "hash", role, "CARLOS01");
        setId(user, UUID.randomUUID());

        lenient().when(renewalRepository.save(any(ZenithRenewalPayment.class)))
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

    private ZenithLicense license(OffsetDateTime currentPeriodEnd, ZenithLicenseStatus status) {
        Purchase purchase = new Purchase(user, PlanType.ZENITH, 1, new BigDecimal("2299.00"), PaymentMethod.CRYPTO);
        purchase.setStatus(PurchaseStatus.CONFIRMED);
        OffsetDateTime activatedAt = currentPeriodEnd.minusYears(1);
        ZenithLicense license = new ZenithLicense(user, purchase, activatedAt, currentPeriodEnd);
        license.setStatus(status);
        setId(license, UUID.randomUUID());
        return license;
    }

    // ---------- renew() ----------

    @Test
    void renew_happyPath_chargesExactlyTwoHundredFifty() {
        ZenithLicense lic = license(OffsetDateTime.now().plusMonths(2), ZenithLicenseStatus.ACTIVE);
        when(licenseRepository.findById(lic.getId())).thenReturn(Optional.of(lic));

        ZenithRenewalPayment payment = service.renew(lic.getId());

        assertThat(payment.getAmount()).isEqualByComparingTo("250.00");
        verify(licenseRepository).save(lic);
    }

    @Test
    void renew_extendsFromPreviousPeriodEnd_notFromNow() {
        OffsetDateTime oldPeriodEnd = OffsetDateTime.now().plusMonths(3); // todavia falta para que venza
        ZenithLicense lic = license(oldPeriodEnd, ZenithLicenseStatus.ACTIVE);
        when(licenseRepository.findById(lic.getId())).thenReturn(Optional.of(lic));

        ZenithRenewalPayment payment = service.renew(lic.getId());

        assertThat(payment.getPeriodStart()).isEqualTo(oldPeriodEnd); // NO "ahora"
        assertThat(payment.getPeriodEnd()).isEqualTo(oldPeriodEnd.plusYears(1));
        assertThat(lic.getCurrentPeriodEnd()).isEqualTo(oldPeriodEnd.plusYears(1));
    }

    @Test
    void renew_reactivatesAnExpiredLicense() {
        ZenithLicense lic = license(OffsetDateTime.now().minusDays(10), ZenithLicenseStatus.EXPIRED);
        when(licenseRepository.findById(lic.getId())).thenReturn(Optional.of(lic));

        service.renew(lic.getId());

        assertThat(lic.getStatus()).isEqualTo(ZenithLicenseStatus.ACTIVE);
    }

    @Test
    void renew_createsARenewalPaymentRecordLinkedToTheLicense() {
        ZenithLicense lic = license(OffsetDateTime.now().plusMonths(1), ZenithLicenseStatus.ACTIVE);
        when(licenseRepository.findById(lic.getId())).thenReturn(Optional.of(lic));

        ArgumentCaptor<ZenithRenewalPayment> captor = ArgumentCaptor.forClass(ZenithRenewalPayment.class);
        service.renew(lic.getId());

        verify(renewalRepository).save(captor.capture());
        assertThat(captor.getValue().getLicense()).isEqualTo(lic);
    }

    @Test
    void renew_unknownId_throwsResourceNotFound() {
        UUID id = UUID.randomUUID();
        when(licenseRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.renew(id));
        verifyNoInteractions(renewalRepository);
    }

    // ---------- expireOverdueLicenses() ----------

    @Test
    void expireOverdueLicenses_onlyTouchesLicensesPastTheirPeriodEnd() {
        ZenithLicense overdue = license(OffsetDateTime.now().minusDays(5), ZenithLicenseStatus.ACTIVE);
        ZenithLicense stillValid = license(OffsetDateTime.now().plusDays(30), ZenithLicenseStatus.ACTIVE);
        when(licenseRepository.findByStatus(ZenithLicenseStatus.ACTIVE)).thenReturn(List.of(overdue, stillValid));

        int expiredCount = service.expireOverdueLicenses();

        assertThat(expiredCount).isEqualTo(1);
        assertThat(overdue.getStatus()).isEqualTo(ZenithLicenseStatus.EXPIRED);
        assertThat(stillValid.getStatus()).isEqualTo(ZenithLicenseStatus.ACTIVE); // no se toco
        verify(licenseRepository).save(overdue);
        verify(licenseRepository, never()).save(stillValid);
    }

    @Test
    void expireOverdueLicenses_noActiveLicenses_returnsZeroAndSavesNothing() {
        when(licenseRepository.findByStatus(ZenithLicenseStatus.ACTIVE)).thenReturn(List.of());

        int expiredCount = service.expireOverdueLicenses();

        assertThat(expiredCount).isEqualTo(0);
        verify(licenseRepository, never()).save(any());
    }
}
