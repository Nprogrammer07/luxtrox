package com.luxtrox.backend.repository;

import com.luxtrox.backend.entity.Referral;
import com.luxtrox.backend.entity.Role;
import com.luxtrox.backend.entity.User;
import com.luxtrox.backend.entity.enums.ReferralStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReferralRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private ReferralRepository referralRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;

    private User createUser(String email, String referralCode) {
        Role role = roleRepository.findByName("USER").orElseThrow();
        return userRepository.save(new User("Test User", email, "+1", "hash", role, referralCode));
    }

    @Test
    void findByReferredReturnsTheSingleReferralRecord() {
        User referrer = createUser("referente@example.com", "REFTEST001");
        User referred = createUser("referido@example.com", "REFTEST002");
        referralRepository.save(new Referral(referrer, referred, "REFTEST001"));

        var result = referralRepository.findByReferred(referred);

        assertThat(result).isPresent();
        assertThat(result.get().getReferrer().getId()).isEqualTo(referrer.getId());
    }

    @Test
    void findByReferrerAndStatusFiltersCorrectly() {
        User referrer = createUser("referente2@example.com", "REFTEST003");
        User referredA = createUser("referidoA@example.com", "REFTEST004");
        User referredB = createUser("referidoB@example.com", "REFTEST005");

        Referral awaiting = new Referral(referrer, referredA, "REFTEST003");
        awaiting.setStatus(ReferralStatus.QUALIFIED_AWAITING_REFERRER);
        referralRepository.save(awaiting);

        Referral paid = new Referral(referrer, referredB, "REFTEST003");
        paid.setStatus(ReferralStatus.BONUS_PAID);
        referralRepository.save(paid);

        List<Referral> awaitingOnly = referralRepository.findByReferrerAndStatus(
                referrer, ReferralStatus.QUALIFIED_AWAITING_REFERRER);

        assertThat(awaitingOnly).hasSize(1);
        assertThat(awaitingOnly.get(0).getReferred().getId()).isEqualTo(referredA.getId());
    }

    @Test
    void referredUserCanOnlyHaveOneReferralRecord() {
        User referrerA = createUser("refA@example.com", "REFTEST006");
        User referrerB = createUser("refB@example.com", "REFTEST007");
        User referred = createUser("referidoUnico@example.com", "REFTEST008");

        referralRepository.saveAndFlush(new Referral(referrerA, referred, "REFTEST006"));
        Referral secondAttempt = new Referral(referrerB, referred, "REFTEST007");

        // referred_user_id es UNIQUE -- un usuario no puede ser
        // "referido" por dos personas distintas.
        org.junit.jupiter.api.Assertions.assertThrows(
                Exception.class,
                () -> referralRepository.saveAndFlush(secondAttempt)
        );
    }
}
