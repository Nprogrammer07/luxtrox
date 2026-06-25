package com.luxtrox.backend.service;

import com.luxtrox.backend.entity.*;
import com.luxtrox.backend.entity.enums.*;
import com.luxtrox.backend.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prueba el algoritmo de comisiones de referido de
 * docs/domain-model.md §7.2 -- el calculo segun el plan comprado, y
 * el reparto entre "avance de una posicion" y "directo al saldo".
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
        purchase.setConfirmedAt(java.time.OffsetDateTime.now());
        return purchaseRepository.save(purchase);
    }

    private InvestmentPosition activePosition(User user, Purchase purchase, BigDecimal capital, BigDecimal paid) {
        BigDecimal target = capital.multiply(PlanPricing.CASHBACK_MULTIPLIER);
        InvestmentPosition position = new InvestmentPosition(user, purchase, capital, target);
        position.setCashbackPaid(paid);
        position.setCashbackRemaining(target.subtract(paid));
        return positionRepository.save(position);
    }

    /**
     * Enlaza referente y referido -- replica EXACTAMENTE lo que hace
     * AuthService.register() en produccion: setea referredBy en el
     * USUARIO referido (no solo crea la fila de Referral). Sin esto,
     * ReferralService.onReferredPurchaseConfirmed() sale de inmediato
     * en su primer guard clause (referredUser.getReferredBy() == null)
     * y ningun test de este archivo prueba nada de verdad.
     */
    private Referral linkReferral(User referrer, User referred) {
        referred.setReferredBy(referrer);
        userRepository.save(referred);
        Referral referral = new Referral(referrer, referred, referrer.getReferralCode());
        return referralRepository.save(referral);
    }

    @Test
    void driverReferral_paysNinePercentOfPurchasePrice() {
        User referrer = createUser("refA@example.com", "REFA0001");
        User referred = createUser("refB@example.com", "REFB0001");
        // Referente ya es elegible: tiene su propia compra confirmada con posicion activa.
        Purchase referrerPurchase = confirmedPurchase(referrer, PlanType.DRIVER, new BigDecimal("1099.00"));
        activePosition(referrer, referrerPurchase, new BigDecimal("1099.00"), BigDecimal.ZERO);
        linkReferral(referrer, referred);

        Purchase referredPurchase = confirmedPurchase(referred, PlanType.DRIVER, new BigDecimal("1099.00"));
        referralService.onReferredPurchaseConfirmed(referredPurchase);

        User refreshedReferrer = userRepository.findById(referrer.getId()).orElseThrow();
        // 1099 * 0.09 = 98.91
        assertThat(refreshedReferrer.getAvailableBalance()).isEqualByComparingTo("98.91");
    }

    @Test
    void zenithReferral_paysFortyPercentOfPurchasePrice() {
        User referrer = createUser("refC@example.com", "REFC0001");
        User referred = createUser("refD@example.com", "REFD0001");
        Purchase referrerPurchase = confirmedPurchase(referrer, PlanType.DRIVER, new BigDecimal("1099.00"));
        activePosition(referrer, referrerPurchase, new BigDecimal("1099.00"), BigDecimal.ZERO);
        linkReferral(referrer, referred);

        Purchase referredPurchase = confirmedPurchase(referred, PlanType.ZENITH, PlanPricing.ZENITH_PRICE);
        referralService.onReferredPurchaseConfirmed(referredPurchase);

        User refreshedReferrer = userRepository.findById(referrer.getId()).orElseThrow();
        // 2299 * 0.40 = 919.60
        assertThat(refreshedReferrer.getAvailableBalance()).isEqualByComparingTo("919.60");
    }

    @Test
    void commissionAppliesAsCashbackAdvanceWhenPositionHasEnoughCapacity() {
        User referrer = createUser("refE@example.com", "REFE0001");
        User referred = createUser("refF@example.com", "REFF0001");
        Purchase referrerPurchase = confirmedPurchase(referrer, PlanType.DRIVER, new BigDecimal("1099.00"));
        InvestmentPosition position = activePosition(referrer, referrerPurchase, new BigDecimal("1099.00"), BigDecimal.ZERO);
        linkReferral(referrer, referred);

        Purchase referredPurchase = confirmedPurchase(referred, PlanType.DRIVER, new BigDecimal("1099.00"));
        referralService.onReferredPurchaseConfirmed(referredPurchase);

        InvestmentPosition refreshedPosition = positionRepository.findById(position.getId()).orElseThrow();
        assertThat(refreshedPosition.getCashbackPaid()).isEqualByComparingTo("98.91");

        var transactions = transactionRepository.findByPosition(refreshedPosition);
        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).getType()).isEqualTo(CashbackTransactionType.REFERRAL_BONUS);
    }

    @Test
    void zenithOnlyReferrer_commissionGoesDirectToBalanceWithNoPosition() {
        User referrer = createUser("refG@example.com", "REFG0001");
        User referred = createUser("refH@example.com", "REFH0001");
        // El referente SOLO tiene Zenith -- nunca compro Driver, no tiene ninguna posicion.
        confirmedPurchase(referrer, PlanType.ZENITH, PlanPricing.ZENITH_PRICE);
        linkReferral(referrer, referred);

        Purchase referredPurchase = confirmedPurchase(referred, PlanType.DRIVER, new BigDecimal("1099.00"));
        referralService.onReferredPurchaseConfirmed(referredPurchase);

        User refreshedReferrer = userRepository.findById(referrer.getId()).orElseThrow();
        assertThat(refreshedReferrer.getAvailableBalance()).isEqualByComparingTo("98.91");

        var directTransactions = transactionRepository.findByUser(refreshedReferrer);
        assertThat(directTransactions).hasSize(1);
        assertThat(directTransactions.get(0).getType()).isEqualTo(CashbackTransactionType.REFERRAL_BONUS_DIRECT);
        assertThat(directTransactions.get(0).getPosition()).isNull();
    }

    @Test
    void commissionExceedingPositionCapacity_splitsBetweenAdvanceAndDirectBalance() {
        User referrer = createUser("refI@example.com", "REFI0001");
        User referred = createUser("refJ@example.com", "REFJ0001");
        Purchase referrerPurchase = confirmedPurchase(referrer, PlanType.DRIVER, new BigDecimal("1099.00"));
        // Solo le quedan 50 de espacio -- la comision de Zenith (919.60) excede por mucho.
        InvestmentPosition position = activePosition(referrer, referrerPurchase, new BigDecimal("1099.00"),
                new BigDecimal("3247.00")); // target=3297, remaining=50

        linkReferral(referrer, referred);
        Purchase referredPurchase = confirmedPurchase(referred, PlanType.ZENITH, PlanPricing.ZENITH_PRICE);
        referralService.onReferredPurchaseConfirmed(referredPurchase);

        InvestmentPosition refreshedPosition = positionRepository.findById(position.getId()).orElseThrow();
        assertThat(refreshedPosition.getCashbackRemaining()).isEqualByComparingTo("0.00");
        assertThat(refreshedPosition.getStatus()).isEqualTo(PositionStatus.COMPLETED);

        User refreshedReferrer = userRepository.findById(referrer.getId()).orElseThrow();
        // SIEMPRE el monto completo al saldo, haya o no posicion de por medio.
        assertThat(refreshedReferrer.getAvailableBalance()).isEqualByComparingTo("919.60");

        var directTransactions = transactionRepository.findByUser(refreshedReferrer);
        assertThat(directTransactions).hasSize(1);
        // 919.60 - 50 (lo que cabia en la posicion) = 869.60 directo al saldo.
        assertThat(directTransactions.get(0).getAmount()).isEqualByComparingTo("869.60");
    }

    @Test
    void referrerNotYetEligible_referralWaitsUntilReferrersFirstConfirmedPurchase() {
        User referrer = createUser("refK@example.com", "REFK0001");
        User referred = createUser("refL@example.com", "REFL0001");
        // El referente NO tiene ninguna compra confirmada todavia.
        Referral referral = linkReferral(referrer, referred);

        Purchase referredPurchase = confirmedPurchase(referred, PlanType.DRIVER, new BigDecimal("1099.00"));
        referralService.onReferredPurchaseConfirmed(referredPurchase);

        Referral afterReferredConfirms = referralRepository.findById(referral.getId()).orElseThrow();
        assertThat(afterReferredConfirms.getStatus()).isEqualTo(ReferralStatus.QUALIFIED_AWAITING_REFERRER);

        User refreshedReferrer = userRepository.findById(referrer.getId()).orElseThrow();
        assertThat(refreshedReferrer.getAvailableBalance()).isEqualByComparingTo("0.00");

        // Ahora el referente hace su PRIMERA compra (puede ser Zenith,
        // no necesita ser Driver) -- esto debe disparar el pago pendiente.
        confirmedPurchase(referrer, PlanType.ZENITH, PlanPricing.ZENITH_PRICE);
        referralService.onReferrerPurchaseConfirmed(referrer);

        Referral afterReferrerConfirms = referralRepository.findById(referral.getId()).orElseThrow();
        assertThat(afterReferrerConfirms.getStatus()).isEqualTo(ReferralStatus.BONUS_PAID);

        User finalReferrer = userRepository.findById(referrer.getId()).orElseThrow();
        assertThat(finalReferrer.getAvailableBalance()).isEqualByComparingTo("98.91");
    }
}