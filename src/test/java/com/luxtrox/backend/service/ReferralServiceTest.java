package com.luxtrox.backend.service;

import com.luxtrox.backend.entity.*;
import com.luxtrox.backend.entity.enums.*;
import com.luxtrox.backend.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prueba el algoritmo de comisiones de referido -- VERSION CORREGIDA
 * (ver docs/domain-model.md adenda de Fase 8). Reemplaza por completo
 * la version de Fase 6: ya no existe "cualquier compra confirmada
 * califica, con reintento" -- ahora cada venta se evalua contra el
 * plan ESPECIFICO del referente, una sola vez, sin segunda oportunidad.
 */
class ReferralServiceTest extends AbstractIntegrationTest {

    @Autowired
    private ReferralService referralService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private PurchaseRepository purchaseRepository;
    @Autowired
    private InvestmentPositionRepository positionRepository;
    @Autowired
    private ZenithLicenseRepository zenithLicenseRepository;
    @Autowired
    private ReferralRepository referralRepository;
    @Autowired
    private CashbackTransactionRepository transactionRepository;

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

    private InvestmentPosition activePosition(User user, Purchase purchase, BigDecimal capital, BigDecimal paid) {
        BigDecimal target = capital.multiply(PlanPricing.CASHBACK_MULTIPLIER);
        InvestmentPosition position = new InvestmentPosition(user, purchase, capital, target);
        position.setCashbackPaid(paid);
        position.setCashbackRemaining(target.subtract(paid));
        return positionRepository.save(position);
    }

    private ZenithLicense activeZenithLicense(User user, Purchase purchase) {
        OffsetDateTime now = OffsetDateTime.now();
        return zenithLicenseRepository.save(new ZenithLicense(user, purchase, now, now.plusYears(1)));
    }

    private ZenithLicense expiredZenithLicense(User user, Purchase purchase) {
        ZenithLicense license = activeZenithLicense(user, purchase);
        license.setStatus(ZenithLicenseStatus.EXPIRED);
        return zenithLicenseRepository.save(license);
    }

    /**
     * Enlaza referente y referido -- replica EXACTAMENTE lo que hace
     * AuthService.register() en produccion: setea referredBy en el
     * USUARIO referido (no solo crea la fila de Referral).
     */
    private Referral linkReferral(User referrer, User referred) {
        referred.setReferredBy(referrer);
        userRepository.save(referred);
        Referral referral = new Referral(referrer, referred, referrer.getReferralCode());
        return referralRepository.save(referral);
    }

    @Test
    void driverReferral_referrerHasActiveDriverPosition_paysNinePercentAsAdvance() {
        User referrer = createUser("refA@example.com", "REFA0001");
        User referred = createUser("refB@example.com", "REFB0001");
        Purchase referrerPurchase = confirmedPurchase(referrer, PlanType.DRIVER, new BigDecimal("1099.00"));
        activePosition(referrer, referrerPurchase, new BigDecimal("1099.00"), BigDecimal.ZERO);
        linkReferral(referrer, referred);

        Purchase referredPurchase = confirmedPurchase(referred, PlanType.DRIVER, new BigDecimal("1099.00"));
        referralService.onReferredPurchaseConfirmed(referredPurchase);

        User refreshedReferrer = userRepository.findById(referrer.getId()).orElseThrow();
        assertThat(refreshedReferrer.getAvailableBalance()).isEqualByComparingTo("98.91"); // 1099 * 0.09
    }

    @Test
    void zenithReferral_referrerHasActiveZenithLicense_paysFortyPercentDirect() {
        User referrer = createUser("refC@example.com", "REFC0001");
        User referred = createUser("refD@example.com", "REFD0001");
        Purchase referrerZenithPurchase = confirmedPurchase(referrer, PlanType.ZENITH, PlanPricing.ZENITH_PRICE);
        activeZenithLicense(referrer, referrerZenithPurchase);
        linkReferral(referrer, referred);

        Purchase referredPurchase = confirmedPurchase(referred, PlanType.ZENITH, PlanPricing.ZENITH_PRICE);
        referralService.onReferredPurchaseConfirmed(referredPurchase);

        User refreshedReferrer = userRepository.findById(referrer.getId()).orElseThrow();
        assertThat(refreshedReferrer.getAvailableBalance()).isEqualByComparingTo("919.60"); // 2299 * 0.40
    }

    @Test
    void driverReferral_referrerHasOnlyZenith_noDriverAtAll_commissionIsEntirelyForfeited() {
        User referrer = createUser("refE@example.com", "REFE0001");
        User referred = createUser("refF@example.com", "REFF0001");
        // El referente SOLO tiene Zenith -- nunca compro Driver.
        Purchase referrerZenithPurchase = confirmedPurchase(referrer, PlanType.ZENITH, PlanPricing.ZENITH_PRICE);
        activeZenithLicense(referrer, referrerZenithPurchase);
        linkReferral(referrer, referred);

        Purchase referredPurchase = confirmedPurchase(referred, PlanType.DRIVER, new BigDecimal("1099.00"));
        referralService.onReferredPurchaseConfirmed(referredPurchase);

        User refreshedReferrer = userRepository.findById(referrer.getId()).orElseThrow();
        assertThat(refreshedReferrer.getAvailableBalance()).isEqualByComparingTo("0.00"); // nada -- se perdio toda

        assertThat(transactionRepository.findByUser(refreshedReferrer)).isEmpty();
    }

    @Test
    void driverReferral_referrersOnlyDriverPositionIsAlreadyCompleted_commissionIsEntirelyForfeited() {
        User referrer = createUser("refG@example.com", "REFG0001");
        User referred = createUser("refH@example.com", "REFH0001");
        Purchase referrerPurchase = confirmedPurchase(referrer, PlanType.DRIVER, new BigDecimal("1099.00"));
        // Ya recibio TODO el cashback de su propio plan -- remaining = 0.
        InvestmentPosition completedPosition = activePosition(referrer, referrerPurchase,
                new BigDecimal("1099.00"), new BigDecimal("3297.00")); // paid = target completo
        completedPosition.setStatus(PositionStatus.COMPLETED);
        positionRepository.save(completedPosition);
        linkReferral(referrer, referred);

        Purchase referredPurchase = confirmedPurchase(referred, PlanType.DRIVER, new BigDecimal("1099.00"));
        referralService.onReferredPurchaseConfirmed(referredPurchase);

        User refreshedReferrer = userRepository.findById(referrer.getId()).orElseThrow();
        assertThat(refreshedReferrer.getAvailableBalance()).isEqualByComparingTo("0.00");
    }

    @Test
    void driverReferral_commissionExceedsRemainingCashback_excessIsLostNotPaidElsewhere() {
        User referrer = createUser("refI@example.com", "REFI0001");
        User referred = createUser("refJ@example.com", "REFJ0001");
        Purchase referrerPurchase = confirmedPurchase(referrer, PlanType.DRIVER, new BigDecimal("1099.00"));
        // Solo le quedan 50 de espacio -- la comision (98.91) excede.
        InvestmentPosition position = activePosition(referrer, referrerPurchase,
                new BigDecimal("1099.00"), new BigDecimal("3247.00")); // target=3297, remaining=50
        linkReferral(referrer, referred);

        Purchase referredPurchase = confirmedPurchase(referred, PlanType.DRIVER, new BigDecimal("1099.00"));
        referralService.onReferredPurchaseConfirmed(referredPurchase);

        InvestmentPosition refreshedPosition = positionRepository.findById(position.getId()).orElseThrow();
        assertThat(refreshedPosition.getCashbackRemaining()).isEqualByComparingTo("0.00");
        assertThat(refreshedPosition.getStatus()).isEqualTo(PositionStatus.COMPLETED);

        User refreshedReferrer = userRepository.findById(referrer.getId()).orElseThrow();
        // Solo se paga lo que cupo (50), NUNCA el monto completo ni el resto a otro lado.
        assertThat(refreshedReferrer.getAvailableBalance()).isEqualByComparingTo("50.00");

        var transactions = transactionRepository.findByPosition(refreshedPosition);
        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).getAmount()).isEqualByComparingTo("50.00");
        // Ninguna transaccion DIRECT por el excedente -- se perdio, no se registro como pago.
        assertThat(transactionRepository.findByUser(refreshedReferrer)).isEmpty();
    }

    @Test
    void zenithReferral_referrerHasOnlyDriver_noZenithAtAll_commissionIsEntirelyForfeited() {
        User referrer = createUser("refK@example.com", "REFK0001");
        User referred = createUser("refL@example.com", "REFL0001");
        Purchase referrerPurchase = confirmedPurchase(referrer, PlanType.DRIVER, new BigDecimal("1099.00"));
        activePosition(referrer, referrerPurchase, new BigDecimal("1099.00"), BigDecimal.ZERO);
        linkReferral(referrer, referred);

        Purchase referredPurchase = confirmedPurchase(referred, PlanType.ZENITH, PlanPricing.ZENITH_PRICE);
        referralService.onReferredPurchaseConfirmed(referredPurchase);

        User refreshedReferrer = userRepository.findById(referrer.getId()).orElseThrow();
        assertThat(refreshedReferrer.getAvailableBalance()).isEqualByComparingTo("0.00");
    }

    @Test
    void zenithReferral_referrersLicenseIsExpired_commissionIsEntirelyForfeited() {
        User referrer = createUser("refM@example.com", "REFM0001");
        User referred = createUser("refN@example.com", "REFN0001");
        Purchase referrerZenithPurchase = confirmedPurchase(referrer, PlanType.ZENITH, PlanPricing.ZENITH_PRICE);
        expiredZenithLicense(referrer, referrerZenithPurchase);
        linkReferral(referrer, referred);

        Purchase referredPurchase = confirmedPurchase(referred, PlanType.ZENITH, PlanPricing.ZENITH_PRICE);
        referralService.onReferredPurchaseConfirmed(referredPurchase);

        User refreshedReferrer = userRepository.findById(referrer.getId()).orElseThrow();
        assertThat(refreshedReferrer.getAvailableBalance()).isEqualByComparingTo("0.00");
    }

    @Test
    void noRetryMechanismExists_referrerBecomingEligibleLaterNeverTriggersThePastCommission() {
        User referrer = createUser("refO@example.com", "REFO0001");
        User referred = createUser("refP@example.com", "REFP0001");
        // El referente NO tiene ningun plan Driver en el momento de la compra del referido.
        Referral referral = linkReferral(referrer, referred);

        Purchase referredPurchase = confirmedPurchase(referred, PlanType.DRIVER, new BigDecimal("1099.00"));
        referralService.onReferredPurchaseConfirmed(referredPurchase);

        Referral resolved = referralRepository.findById(referral.getId()).orElseThrow();
        assertThat(resolved.getStatus()).isEqualTo(ReferralStatus.RESOLVED); // se evaluo y se resolvio (perdida)

        User refreshedReferrer = userRepository.findById(referrer.getId()).orElseThrow();
        assertThat(refreshedReferrer.getAvailableBalance()).isEqualByComparingTo("0.00");

        // El referente AHORA SI compra Driver -- pero no existe ningun
        // metodo que reevalue la referral pasada. La comision queda
        // perdida para siempre, tal como se confirmo con el cliente.
        Purchase laterPurchase = confirmedPurchase(referrer, PlanType.DRIVER, new BigDecimal("1099.00"));
        activePosition(referrer, laterPurchase, new BigDecimal("1099.00"), BigDecimal.ZERO);

        User stillSameReferrer = userRepository.findById(referrer.getId()).orElseThrow();
        assertThat(stillSameReferrer.getAvailableBalance()).isEqualByComparingTo("0.00"); // sin cambios
    }

    @Test
    void referrerOwningBothPlans_canEarnBothCommissionsIndependently() {
        User referrer = createUser("refQ@example.com", "REFQ0001");
        User referred = createUser("refR@example.com", "REFR0001");
        Purchase referrerDriverPurchase = confirmedPurchase(referrer, PlanType.DRIVER, new BigDecimal("1099.00"));
        activePosition(referrer, referrerDriverPurchase, new BigDecimal("1099.00"), BigDecimal.ZERO);
        Purchase referrerZenithPurchase = confirmedPurchase(referrer, PlanType.ZENITH, PlanPricing.ZENITH_PRICE);
        activeZenithLicense(referrer, referrerZenithPurchase);
        linkReferral(referrer, referred);

        // El mismo referido compra AMBOS planes -- cada venta se evalua
        // independientemente contra el plan correspondiente del referente.
        Purchase referredDriverPurchase = confirmedPurchase(referred, PlanType.DRIVER, new BigDecimal("1099.00"));
        referralService.onReferredPurchaseConfirmed(referredDriverPurchase);

        Purchase referredZenithPurchase = confirmedPurchase(referred, PlanType.ZENITH, PlanPricing.ZENITH_PRICE);
        referralService.onReferredPurchaseConfirmed(referredZenithPurchase);

        User refreshedReferrer = userRepository.findById(referrer.getId()).orElseThrow();
        // 98.91 (Driver, avance a su posicion) + 919.60 (Zenith, directo) = 1018.51
        assertThat(refreshedReferrer.getAvailableBalance()).isEqualByComparingTo("1018.51");
    }
}
