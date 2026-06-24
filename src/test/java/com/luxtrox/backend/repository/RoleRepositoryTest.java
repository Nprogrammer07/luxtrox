package com.luxtrox.backend.repository;

import com.luxtrox.backend.entity.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class RoleRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private RoleRepository roleRepository;

    @Test
    void seedDataHasAdminAndUserRoles() {
        // V1__create_roles.sql siembra estos dos roles -- si este test
        // falla, algo rompio el seed de la migracion.
        assertThat(roleRepository.findByName("ADMIN")).isPresent();
        assertThat(roleRepository.findByName("USER")).isPresent();
    }

    @Test
    void findByNameReturnsEmptyForUnknownRole() {
        assertThat(roleRepository.findByName("SUPER_ADMIN")).isEmpty();
    }

    @Test
    void roleNameIsUnique() {
        Role duplicate = new Role("ADMIN", "intento de duplicado");
        org.junit.jupiter.api.Assertions.assertThrows(
                Exception.class,
                () -> roleRepository.saveAndFlush(duplicate)
        );
    }
}
