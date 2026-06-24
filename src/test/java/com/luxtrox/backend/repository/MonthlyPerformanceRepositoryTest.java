package com.luxtrox.backend.repository;

import com.luxtrox.backend.entity.MonthlyPerformance;
import com.luxtrox.backend.entity.Role;
import com.luxtrox.backend.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MonthlyPerformanceRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private MonthlyPerformanceRepository performanceRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;

    private User createAdmin(String email) {
        Role role = roleRepository.findByName("ADMIN").orElseThrow();
        return userRepository.save(new User("Admin Test", email, "+1", "hash", role, "ADMINTEST" + email.hashCode()));
    }

    @Test
    void findByMonthAndYearFindsTheRegisteredPerformance() {
        User admin = createAdmin("admin1@example.com");
        performanceRepository.save(new MonthlyPerformance(7, 2026, new BigDecimal("10.00"), admin));

        var result = performanceRepository.findByMonthAndYear(7, 2026);

        assertThat(result).isPresent();
        assertThat(result.get().getPercentage()).isEqualByComparingTo("10.00");
    }

    @Test
    void onlyOnePerformancePerCalendarMonthIsAllowed() {
        User admin = createAdmin("admin2@example.com");
        performanceRepository.saveAndFlush(new MonthlyPerformance(8, 2026, new BigDecimal("10.00"), admin));

        MonthlyPerformance duplicate = new MonthlyPerformance(8, 2026, new BigDecimal("15.00"), admin);

        // UNIQUE(month, year) en la BD -- este test confirma que ni
        // siquiera Hibernate puede saltarse esa restriccion.
        assertThrows(
                DataIntegrityViolationException.class,
                () -> performanceRepository.saveAndFlush(duplicate)
        );
    }

    @Test
    void sameMonthDifferentYearIsAllowed() {
        User admin = createAdmin("admin3@example.com");
        performanceRepository.saveAndFlush(new MonthlyPerformance(9, 2025, new BigDecimal("5.00"), admin));
        performanceRepository.saveAndFlush(new MonthlyPerformance(9, 2026, new BigDecimal("7.00"), admin));

        assertThat(performanceRepository.findByMonthAndYear(9, 2025)).isPresent();
        assertThat(performanceRepository.findByMonthAndYear(9, 2026)).isPresent();
    }
}
