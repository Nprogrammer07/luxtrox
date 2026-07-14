package com.luxtrox.backend.unit.service;

import com.luxtrox.backend.entity.*;
import com.luxtrox.backend.entity.enums.*;
import com.luxtrox.backend.exception.ResourceNotFoundException;
import com.luxtrox.backend.repository.PlusLicenseRepository;
import com.luxtrox.backend.repository.PlusRenewalPaymentRepository;
import com.luxtrox.backend.service.AuditService;
import com.luxtrox.backend.service.PlusService;
import com.luxtrox.backend.service.ReferralService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Luxtrox Genius (PLUS) -- matrícula anual de $200 renovable.
 * Cada renovación paga $50 de comisión al referente del alumno.
 */
@ExtendWith(MockitoExtension.class)
class PlusServiceUnitTest {

    @Mock private PlusLicenseRepository licenseRepository;
    @Mock private PlusRenewalPaymentRepository renewalRepository;
    @Mock private ReferralService referralService;
    @Mock private AuditService auditService;

    private PlusService service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new PlusService(licenseRepository, renewalRepository, referralService, auditService);

        Role role = new Role("USER", "Usuario estandar");
        user = new User("Alumno", "alumno@example.com", "+1", "hash", role, "ALUM0001");
        setId(user, UUID.randomUUID());

        lenient().when(licenseRepository.save(any(PlusLicense.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        lenient().when(renewalRepository.save(any(PlusRenewalPayment.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    private void setId(Object entity, UUID id) {
        try {
            var f = entity.getClass().getDeclaredField("id");
            f.setAccessible(true);
            f.set(entity, id);
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    private PlusLicense licenseWithPeriodEnd(OffsetDateTime periodEnd) {
        PlusLicense license = new PlusLicense(user, null);
        setId(license, UUID.randomUUID());
        license.setCurrentPeriodEnd(periodEnd);
        return license;
    }

    // ---------- createLicense() ----------

    @Test
    void createLicense_givesOneYearOfAccess_notFive() {
        PlusLicense license = service.createLicense(user, null);

        long daysOfAccess = ChronoUnit.DAYS.between(
                license.getPurchasedAt(), license.getCurrentPeriodEnd());
        assertThat(daysOfAccess).isBetween(364L, 366L);
        assertThat(license.getStatus()).isEqualTo(PlusLicenseStatus.ACTIVE);
    }

    // ---------- renew() ----------

    @Test
    void renew_extendsPeriodByOneYear_fromPreviousEnd() {
        OffsetDateTime oldEnd = OffsetDateTime.now().plusDays(10);
        PlusLicense license = licenseWithPeriodEnd(oldEnd);
        when(licenseRepository.findById(license.getId())).thenReturn(Optional.of(license));

        service.renew(license.getId());

        // El nuevo periodo arranca donde terminaba el anterior (no desde hoy)
        assertThat(license.getCurrentPeriodEnd()).isEqualTo(oldEnd.plusYears(1));
    }

    @Test
    void renew_recordsPaymentOfExactly200() {
        PlusLicense license = licenseWithPeriodEnd(OffsetDateTime.now().plusDays(5));
        when(licenseRepository.findById(license.getId())).thenReturn(Optional.of(license));

        service.renew(license.getId());

        ArgumentCaptor<PlusRenewalPayment> captor = ArgumentCaptor.forClass(PlusRenewalPayment.class);
        verify(renewalRepository).save(captor.capture());
        assertThat(captor.getValue().getAmount()).isEqualByComparingTo("200.00");
    }

    @Test
    void renew_paysReferralCommissionOnEveryRenewal() {
        PlusLicense license = licenseWithPeriodEnd(OffsetDateTime.now().plusDays(5));
        when(licenseRepository.findById(license.getId())).thenReturn(Optional.of(license));

        service.renew(license.getId());

        // Cada renovación paga comisión -- no solo la primera compra
        verify(referralService).onRenewalConfirmed(
                eq(user), eq(PlanType.PLUS), eq(new BigDecimal("200.00")));
    }

    @Test
    void renew_expiredLicense_reactivatesIt() {
        PlusLicense license = licenseWithPeriodEnd(OffsetDateTime.now().minusDays(30));
        license.setStatus(PlusLicenseStatus.EXPIRED);
        when(licenseRepository.findById(license.getId())).thenReturn(Optional.of(license));

        service.renew(license.getId());

        assertThat(license.getStatus()).isEqualTo(PlusLicenseStatus.ACTIVE);
    }

    @Test
    void renew_unknownLicense_throwsNotFound() {
        UUID id = UUID.randomUUID();
        when(licenseRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.renew(id));
        verifyNoInteractions(referralService, renewalRepository);
    }

    // ---------- expireOverdueLicenses() ----------

    @Test
    void expireOverdue_marksOnlyThoseAlreadyPastTheirPeriod() {
        PlusLicense expired = licenseWithPeriodEnd(OffsetDateTime.now().minusDays(1));
        PlusLicense stillValid = licenseWithPeriodEnd(OffsetDateTime.now().plusDays(1));
        when(licenseRepository.findByStatus(PlusLicenseStatus.ACTIVE))
                .thenReturn(List.of(expired, stillValid));

        int count = service.expireOverdueLicenses();

        assertThat(count).isEqualTo(1);
        assertThat(expired.getStatus()).isEqualTo(PlusLicenseStatus.EXPIRED);
        assertThat(stillValid.getStatus()).isEqualTo(PlusLicenseStatus.ACTIVE);
    }

    @Test
    void expireOverdue_noneOverdue_returnsZero() {
        when(licenseRepository.findByStatus(PlusLicenseStatus.ACTIVE))
                .thenReturn(List.of(licenseWithPeriodEnd(OffsetDateTime.now().plusMonths(6))));

        assertThat(service.expireOverdueLicenses()).isZero();
    }

    // ---------- hasActiveLicense() -- gobierna el descuento Zenith ----------

    @Test
    void hasActiveLicense_expiredMatricula_losesTheZenithDiscount() {
        when(licenseRepository.existsByUserAndStatus(user, PlusLicenseStatus.ACTIVE))
                .thenReturn(false);

        assertThat(service.hasActiveLicense(user)).isFalse();
    }

    @Test
    void hasActiveLicense_activeMatricula_keepsTheZenithDiscount() {
        when(licenseRepository.existsByUserAndStatus(user, PlusLicenseStatus.ACTIVE))
                .thenReturn(true);

        assertThat(service.hasActiveLicense(user)).isTrue();
    }
}
