package com.luxtrox.backend.service;

import com.luxtrox.backend.entity.*;
import com.luxtrox.backend.entity.enums.*;
import com.luxtrox.backend.repository.*;
import com.luxtrox.backend.service.PlanPricing;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prueba el algoritmo de comisiones de referido -- VERSION SIMPLIFICADA.
 * Sin restricciones de plan: el referente recibe comision por CADA
 * compra confirmada del referido, sin importar si tiene un plan propio.
 */
class ReferralServiceTest extends AbstractIntegrationTest {

    @Autowired private ReferralService referralService;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PurchaseRepository purchaseRepository;
    @Autowired private ReferralRepository referralRepository;
    @Autowired private CashbackTransactionRepository transactionRepository;

    private User createUser(String email, String referralCode) {
        Role role = roleRepository.findByName("USER").orElseThrow();
        return userRepository.save(new User("Test", email, "+1", "hash", role, referralCode));
    }

    private Purchase confirmedPurchase(User user, PlanType plan, BigDecimal amount) {
        Purchase purchase = new Purchase(user, plan, 1, amount, PaymentMethod.CRYPTO);
        purchase.setStatus(PurchaseStatus.CONFIRMED);
        purchase.setConfirmedAt(OffsetDateTime.now());
        return purchaseRepository.save(purchase);
    }

    private Referral linkReferral(User referrer, User referred) {
        referred.setReferredBy(referrer);
        userRepository.save(referred);
        return referralRepository.save(new Referral(referrer, referred, referrer.getReferralCode()));
    }

    // ---------- Sin restricciones de plan ----------

    @Test
    void driverPurchase_referrerWithNoPlan_receivesNinePercent() {
        User referrer = createUser("refA@example.com", "REFA0001");
        User referred = createUser("refB@example.com", "REFB0001");
        linkReferral(referrer, referred);

        Purchase referredPurchase = confirmedPurchase(referred, PlanType.DRIVER, new BigDecimal("1099.00"));
        referralService.onReferredPurchaseConfirmed(referredPurchase);

        User refreshed = userRepository.findById(referrer.getId()).orElseThrow();
        assertThat(refreshed.getAvailableBalance()).isEqualByComparingTo("98.91"); // 1099 * 0.09
        assertThat(transactionRepository.findByUser(refreshed)).hasSize(1);
    }

    @Test
    void zenithPurchase_referrerWithNoPlan_receivesTwentyTwoPercent() {
        User referrer = createUser("refC@example.com", "REFC0001");
        User referred = createUser("refD@example.com", "REFD0001");
        linkReferral(referrer, referred);

        Purchase referredPurchase = confirmedPurchase(referred, PlanType.ZENITH, PlanPricing.ZENITH_PRICE);
        referralService.onReferredPurchaseConfirmed(referredPurchase);

        User refreshed = userRepository.findById(referrer.getId()).orElseThrow();
        assertThat(refreshed.getAvailableBalance()).isEqualByComparingTo("505.78"); // 2299 * 0.22
    }

    // ---------- Por compra (repetible) ----------

    @Test
    void sameReferredPerson_buyingDriverThenZenith_generatesTwoCommissions() {
        User referrer = createUser("refE@example.com", "REFE0001");
        User referred = createUser("refF@example.com", "REFF0001");
        linkReferral(referrer, referred);

        // Primera compra (Driver) → 98.91
        Purchase driverPurchase = confirmedPurchase(referred, PlanType.DRIVER, new BigDecimal("1099.00"));
        referralService.onReferredPurchaseConfirmed(driverPurchase);

        User afterFirst = userRepository.findById(referrer.getId()).orElseThrow();
        assertThat(afterFirst.getAvailableBalance()).isEqualByComparingTo("98.91");

        // Segunda compra (Zenith) → 505.78 ADICIONALES -- ya no se bloquea por RESOLVED
        Purchase zenithPurchase = confirmedPurchase(referred, PlanType.ZENITH, PlanPricing.ZENITH_PRICE);
        referralService.onReferredPurchaseConfirmed(zenithPurchase);

        User afterSecond = userRepository.findById(referrer.getId()).orElseThrow();
        assertThat(afterSecond.getAvailableBalance()).isEqualByComparingTo("604.69"); // 98.91 + 505.78
        assertThat(transactionRepository.findByUser(afterSecond)).hasSize(2);
    }

    @Test
    void sameReferredPerson_buyingThreeDrivers_generatesSeparateCommissionEachTime() {
        User referrer = createUser("refG@example.com", "REFG0001");
        User referred = createUser("refH@example.com", "REFH0001");
        linkReferral(referrer, referred);

        for (int i = 0; i < 3; i++) {
            Purchase purchase = confirmedPurchase(referred, PlanType.DRIVER, new BigDecimal("1099.00"));
            referralService.onReferredPurchaseConfirmed(purchase);
        }

        User refreshed = userRepository.findById(referrer.getId()).orElseThrow();
        // 3 × 98.91 = 296.73
        assertThat(refreshed.getAvailableBalance()).isEqualByComparingTo("296.73");
        assertThat(transactionRepository.findByUser(refreshed)).hasSize(3);
    }

    // ---------- Referral.status: PENDING → RESOLVED en la primera compra ----------

    @Test
    void firstPurchase_marksReferralResolved_subsequentPurchasesStillPayCommission() {
        User referrer = createUser("refI@example.com", "REFI0001");
        User referred = createUser("refJ@example.com", "REFJ0001");
        Referral referral = linkReferral(referrer, referred);
        assertThat(referral.getStatus()).isEqualTo(ReferralStatus.PENDING_PURCHASE);

        Purchase first = confirmedPurchase(referred, PlanType.DRIVER, new BigDecimal("1099.00"));
        referralService.onReferredPurchaseConfirmed(first);

        Referral afterFirst = referralRepository.findById(referral.getId()).orElseThrow();
        assertThat(afterFirst.getStatus()).isEqualTo(ReferralStatus.RESOLVED);

        // Segunda compra -- RESOLVED ya no bloquea
        Purchase second = confirmedPurchase(referred, PlanType.DRIVER, new BigDecimal("1099.00"));
        referralService.onReferredPurchaseConfirmed(second);

        User refreshed = userRepository.findById(referrer.getId()).orElseThrow();
        assertThat(refreshed.getAvailableBalance()).isEqualByComparingTo("197.82"); // 98.91 × 2
    }

    // ---------- Personas distintas → comisiones independientes ----------

    @Test
    void differentReferredPeople_eachGenerateTheirOwnCommission_unlimited() {
        User referrer = createUser("refK@example.com", "REFK0001");

        User firstReferred = createUser("refL@example.com", "REFL0001");
        linkReferral(referrer, firstReferred);
        referralService.onReferredPurchaseConfirmed(
                confirmedPurchase(firstReferred, PlanType.DRIVER, new BigDecimal("1099.00")));

        User secondReferred = createUser("refM@example.com", "REFM0001");
        linkReferral(referrer, secondReferred);
        referralService.onReferredPurchaseConfirmed(
                confirmedPurchase(secondReferred, PlanType.DRIVER, new BigDecimal("1099.00")));

        User refreshed = userRepository.findById(referrer.getId()).orElseThrow();
        assertThat(refreshed.getAvailableBalance()).isEqualByComparingTo("197.82"); // 98.91 × 2
        assertThat(transactionRepository.findByUser(refreshed)).hasSize(2);
    }

    // ---------- Sin referente → no ocurre nada ----------

    @Test
    void purchaserWithNoReferrer_generatesNoCommission() {
        User solo = createUser("solo@example.com", "SOLO0001");
        Purchase purchase = confirmedPurchase(solo, PlanType.DRIVER, new BigDecimal("1099.00"));

        referralService.onReferredPurchaseConfirmed(purchase);

        User refreshed = userRepository.findById(solo.getId()).orElseThrow();
        assertThat(refreshed.getAvailableBalance()).isEqualByComparingTo("0.00");
    }
}
