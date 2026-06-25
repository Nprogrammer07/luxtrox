package com.luxtrox.backend.service;

import com.luxtrox.backend.entity.*;
import com.luxtrox.backend.entity.enums.PaymentMethod;
import com.luxtrox.backend.entity.enums.PlanType;
import com.luxtrox.backend.entity.enums.ZenithLicenseStatus;
import com.luxtrox.backend.repository.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prueba la renovacion anual de Zenith (docs/domain-model.md 7.1):
 * siempre $250 fijos, extiende desde el periodo anterior (no desde
 * "ahora"), y el job de expiracion solo toca licencias vencidas.
 */
class ZenithServiceTest extends AbstractIntegrationTest {

    @Autowired
    private ZenithService zenithService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private PurchaseRepository purchaseRepository;
    @Autowired
    private ZenithLicenseRepository licenseRepository;
    @Autowired
    private ZenithRenewalPaymentRepository renewalRepository;
    @Autowired
    private EntityManager entityManager;

    private User createUser(String email) {
        Role role = roleRepository.findByName("USER").orElseThrow();
        return userRepository.save(new User("Test", email, "+1", "hash", role, "REF" + email.hashCode()));
    }

    private ZenithLicense createLicense(User user, OffsetDateTime activatedAt, OffsetDateTime periodEnd) {
        Purchase purchase = purchaseRepository.save(
                new Purchase(user, PlanType.ZENITH, 1, PlanPricing.ZENITH_PRICE, PaymentMethod.CRYPTO));
        ZenithLicense license = new ZenithLicense(user, purchase, activatedAt, periodEnd);
        return licenseRepository.save(license);
    }

    @Test
    void renewExtendsFromThePreviousPeriodEnd_notFromNow() {
        User user = createUser("renew1@example.com");
        OffsetDateTime activatedAt = OffsetDateTime.now().minusMonths(11);
        OffsetDateTime periodEnd = activatedAt.plusYears(1); // vence en 1 mes
        ZenithLicense license = createLicense(user, activatedAt, periodEnd);

        zenithService.renew(license.getId());

        ZenithLicense refreshed = licenseRepository.findById(license.getId()).orElseThrow();
        // La nueva fecha es periodEnd + 1 año, NO "ahora" + 1 año --
        // si renuevas temprano no deberias perder el tiempo que te
        // quedaba.
        assertThat(refreshed.getCurrentPeriodEnd()).isEqualToIgnoringNanos(periodEnd.plusYears(1));
        assertThat(refreshed.getStatus()).isEqualTo(ZenithLicenseStatus.ACTIVE);
    }

    @Test
    void renewAlwaysChargesExactlyTwoHundredFifty() {
        User user = createUser("renew2@example.com");
        OffsetDateTime now = OffsetDateTime.now();
        ZenithLicense license = createLicense(user, now.minusYears(1), now);

        ZenithRenewalPayment payment = zenithService.renew(license.getId());

        assertThat(payment.getAmount()).isEqualByComparingTo("250.00");
    }

    @Test
    void renewingAnExpiredLicenseReactivatesIt() {
        User user = createUser("renew3@example.com");
        OffsetDateTime now = OffsetDateTime.now();
        ZenithLicense license = createLicense(user, now.minusYears(1).minusMonths(2), now.minusMonths(2));

        entityManager.createNativeQuery("UPDATE zenith_licenses SET status = 'EXPIRED' WHERE id = :id")
                .setParameter("id", license.getId())
                .executeUpdate();
        entityManager.clear();

        zenithService.renew(license.getId());

        ZenithLicense refreshed = licenseRepository.findById(license.getId()).orElseThrow();
        assertThat(refreshed.getStatus()).isEqualTo(ZenithLicenseStatus.ACTIVE);
    }

    @Test
    void expireOverdueLicenses_onlyTouchesLicensesPastTheirPeriodEnd() {
        User userExpired = createUser("expired@example.com");
        User userActive = createUser("active@example.com");

        ZenithLicense expiredOne = createLicense(userExpired,
                OffsetDateTime.now().minusYears(2), OffsetDateTime.now().minusDays(1)); // ya vencio
        ZenithLicense activeOne = createLicense(userActive,
                OffsetDateTime.now().minusMonths(1), OffsetDateTime.now().plusMonths(11)); // todavia vigente

        int expiredCount = zenithService.expireOverdueLicenses();

        assertThat(expiredCount).isEqualTo(1);
        assertThat(licenseRepository.findById(expiredOne.getId()).orElseThrow().getStatus())
                .isEqualTo(ZenithLicenseStatus.EXPIRED);
        assertThat(licenseRepository.findById(activeOne.getId()).orElseThrow().getStatus())
                .isEqualTo(ZenithLicenseStatus.ACTIVE);
    }

    @Test
    void renewCreatesARenewalPaymentRecordLinkedToTheLicense() {
        User user = createUser("renew4@example.com");
        OffsetDateTime now = OffsetDateTime.now();
        ZenithLicense license = createLicense(user, now.minusYears(1), now);

        zenithService.renew(license.getId());

        var payments = renewalRepository.findByLicense(license);
        assertThat(payments).hasSize(1);
    }
}
