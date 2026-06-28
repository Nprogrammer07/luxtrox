package com.luxtrox.backend.config;

import com.luxtrox.backend.entity.Role;
import com.luxtrox.backend.entity.User;
import com.luxtrox.backend.repository.RoleRepository;
import com.luxtrox.backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminBootstrapRunnerUnitTest {

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;

    private void setId(Object entity, UUID id) {
        try {
            var field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private User userWithRole(String roleName) {
        Role role = new Role(roleName, "rol de prueba");
        User user = new User("Test", "admin@example.com", "+1", "hash", role, "TEST0001");
        setId(user, UUID.randomUUID());
        return user;
    }

    @Test
    void blankEmail_doesNothingAtAll() {
        var runner = new AdminBootstrapRunner(userRepository, roleRepository, "");

        runner.run(null);

        verifyNoInteractions(userRepository, roleRepository);
    }

    @Test
    void nullEmail_doesNothingAtAll() {
        var runner = new AdminBootstrapRunner(userRepository, roleRepository, null);

        runner.run(null);

        verifyNoInteractions(userRepository, roleRepository);
    }

    @Test
    void emailConfigured_butUserDoesNotExistYet_doesNothing() {
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.empty());
        var runner = new AdminBootstrapRunner(userRepository, roleRepository, "admin@example.com");

        runner.run(null);

        verify(userRepository, never()).save(any());
        verifyNoInteractions(roleRepository);
    }

    @Test
    void userAlreadyAdmin_isIdempotent_neverTouchesRoleRepository() {
        User admin = userWithRole("ADMIN");
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(admin));
        var runner = new AdminBootstrapRunner(userRepository, roleRepository, "admin@example.com");

        runner.run(null);

        verify(userRepository, never()).save(any());
        verifyNoInteractions(roleRepository); // ni siquiera busca el rol ADMIN -- corta antes
    }

    @Test
    void userIsRegularUser_getsPromotedToAdminAndSaved() {
        User regularUser = userWithRole("USER");
        Role adminRole = new Role("ADMIN", "Administrador");
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(regularUser));
        when(roleRepository.findByName("ADMIN")).thenReturn(Optional.of(adminRole));

        var runner = new AdminBootstrapRunner(userRepository, roleRepository, "admin@example.com");
        runner.run(null);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getRole().getName()).isEqualTo("ADMIN");
    }

    @Test
    void adminRoleMissingFromDatabase_throwsClearException() {
        User regularUser = userWithRole("USER");
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(regularUser));
        when(roleRepository.findByName("ADMIN")).thenReturn(Optional.empty());

        var runner = new AdminBootstrapRunner(userRepository, roleRepository, "admin@example.com");

        assertThrows(IllegalStateException.class, () -> runner.run(null));
        verify(userRepository, never()).save(any());
    }
}