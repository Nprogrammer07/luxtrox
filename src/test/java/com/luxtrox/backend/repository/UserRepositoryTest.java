package com.luxtrox.backend.repository;

import com.luxtrox.backend.entity.Role;
import com.luxtrox.backend.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class UserRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Test
    void findByEmailFindsExistingUser() {
        Role userRole = roleRepository.findByName("USER").orElseThrow();
        User user = new User("Carlos Mendoza", "carlos@example.com", "+57300",
                "hash", userRole, "CARLOS001");
        userRepository.save(user);

        assertThat(userRepository.findByEmail("carlos@example.com")).isPresent();
        assertThat(userRepository.findByEmail("no-existe@example.com")).isEmpty();
    }

    @Test
    void findByReferralCodeFindsExistingUser() {
        Role userRole = roleRepository.findByName("USER").orElseThrow();
        User user = new User("Carlos Mendoza", "carlos2@example.com", "+57300",
                "hash", userRole, "CARLOS002");
        userRepository.save(user);

        assertThat(userRepository.findByReferralCode("CARLOS002")).isPresent();
    }

    @Test
    void existsByEmailReflectsActualState() {
        Role userRole = roleRepository.findByName("USER").orElseThrow();
        assertThat(userRepository.existsByEmail("nuevo@example.com")).isFalse();

        userRepository.save(new User("Nuevo", "nuevo@example.com", "+1",
                "hash", userRole, "NUEVO001"));

        assertThat(userRepository.existsByEmail("nuevo@example.com")).isTrue();
    }

    @Test
    void emailUniqueConstraintIsEnforced() {
        Role userRole = roleRepository.findByName("USER").orElseThrow();
        userRepository.saveAndFlush(new User("Uno", "dup@example.com", "+1",
                "hash", userRole, "DUP001"));

        User duplicateEmail = new User("Otro", "dup@example.com", "+2",
                "hash", userRole, "DUP002");

        org.junit.jupiter.api.Assertions.assertThrows(
                Exception.class,
                () -> userRepository.saveAndFlush(duplicateEmail)
        );
    }
}
