package com.luxtrox.backend.service;

import com.luxtrox.backend.entity.Purchase;
import com.luxtrox.backend.entity.Role;
import com.luxtrox.backend.entity.User;
import com.luxtrox.backend.entity.enums.PaymentMethod;
import com.luxtrox.backend.entity.enums.PlanType;
import com.luxtrox.backend.entity.enums.PurchaseStatus;
import com.luxtrox.backend.exception.BusinessRuleException;
import com.luxtrox.backend.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PurchaseServiceTest extends AbstractIntegrationTest {

    @Autowired
    private PurchaseService purchaseService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private InvestmentPositionRepository positionRepository;
    @Autowired
    private ZenithLicenseRepository zenithLicenseRepository;

    private User createUser(String email) {
        Role role = roleRepository.findByName("USER").orElseThrow();
        return userRepository.save(new User("Test", email, "+1", "hash", role, "REF" + email.hashCode()));
    }

    @Test
    void createDriverPurchase_rejectsQuantityAboveThirty() {
        User user = createUser("driver1@example.com");
        assertThrows(BusinessRuleException.class,
                () -> purchaseService.createDriverPurchase(user, 31, PaymentMethod.CRYPTO));
    }

    @Test
    void createDriverPurchase_rejectsWhenAccumulatedTotalWouldExceedThirty() {
        User user = createUser("driver2@example.com");
        purchaseService.confirmPurchase(
                purchaseService.createDriverPurchase(user, 25, PaymentMethod.CRYPTO).getId());

        User refreshed = userRepository.findById(user.getId()).orElseThrow();
        assertThrows(BusinessRuleException.class,
                () -> purchaseService.createDriverPurchase(refreshed, 10, PaymentMethod.CRYPTO));
    }

    @Test
    void confirmingADriverPurchase_createsAnActivePositionWithTripleTarget() {
        User user = createUser("driver3@example.com");
        Purchase purchase = purchaseService.createDriverPurchase(user, 2, PaymentMethod.CRYPTO);

        Purchase confirmed = purchaseService.confirmPurchase(purchase.getId());

        assertThat(confirmed.getStatus()).isEqualTo(PurchaseStatus.CONFIRMED);
        assertThat(confirmed.getTotalAmount()).isEqualByComparingTo("2198.00"); // 2 x 1099
        assertThat(confirmed.getPosition()).isNotNull();
        assertThat(confirmed.getPosition().getTargetCashback()).isEqualByComparingTo("6594.00"); // x3

        User refreshedUser = userRepository.findById(user.getId()).orElseThrow();
        assertThat(refreshedUser.getTotalPackagesPurchased()).isEqualTo(2);
    }

    @Test
    void confirmingAZenithPurchase_createsALicenseInsteadOfAPosition() {
        User user = createUser("zenith1@example.com");
        Purchase purchase = purchaseService.createZenithPurchase(user, PaymentMethod.CRYPTO);

        Purchase confirmed = purchaseService.confirmPurchase(purchase.getId());

        assertThat(confirmed.getTotalAmount()).isEqualByComparingTo("2299.00");
        assertThat(confirmed.getPosition()).isNull(); // Zenith NUNCA tiene posicion

        var licenses = zenithLicenseRepository.findByUser(user);
        assertThat(licenses).hasSize(1);

        User refreshedUser = userRepository.findById(user.getId()).orElseThrow();
        // Zenith no cuenta para el tope de 30 paquetes de Driver.
        assertThat(refreshedUser.getTotalPackagesPurchased()).isEqualTo(0);
    }

    @Test
    void confirmingAnAlreadyConfirmedPurchase_isIdempotent() {
        User user = createUser("idemp@example.com");
        Purchase purchase = purchaseService.createDriverPurchase(user, 1, PaymentMethod.CRYPTO);

        purchaseService.confirmPurchase(purchase.getId());
        purchaseService.confirmPurchase(purchase.getId()); // segunda vez, no debe duplicar nada

        User refreshedUser = userRepository.findById(user.getId()).orElseThrow();
        assertThat(refreshedUser.getTotalPackagesPurchased()).isEqualTo(1); // no 2

        assertThat(positionRepository.findByUserAndStatus(refreshedUser,
                com.luxtrox.backend.entity.enums.PositionStatus.ACTIVE)).hasSize(1);
    }
}
