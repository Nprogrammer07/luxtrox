package com.luxtrox.backend.repository;

import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base comun para los tests de repositorio de Fase 4. Cada clase que
 * extiende esta base levanta su PROPIO Postgres real en un contenedor
 * Docker (aislamiento total entre clases, a proposito) y corre las 15
 * migraciones de Flyway sobre el.
 *
 * @DirtiesContext es necesario aqui: sin el, Spring puede reusar el
 * ApplicationContext (y por lo tanto el DataSource/HikariPool) de una
 * clase de test anterior en la siguiente, aunque cada clase tenga su
 * propio contenedor en un puerto distinto -- eso deja al pool
 * apuntando a un contenedor que ya no existe. Forzamos un contexto
 * fresco por clase para que esto no pase.
 *
 * @Transactional envuelve cada metodo de test en su propia
 * transaccion con rollback automatico al terminar -- ademas de evitar
 * que los datos de un test se filtren a otro dentro de la misma
 * clase, es requisito para poder ejecutar la query nativa de UPDATE
 * que usan algunos tests (Hibernate exige una transaccion activa para
 * eso, a diferencia de un simple repository.save()).
 *
 * Requiere Docker corriendo en la maquina que ejecuta los tests.
 *
 * Tambien registra un app.jwt.secret de prueba -- application.yml
 * exige esa propiedad sin valor por defecto (por seguridad, en real
 * viene de una variable de entorno), asi que sin esto los tests de
 * Fase 5 en adelante no podrian arrancar el contexto.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional
public abstract class AbstractIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("luxtrox_test")
                    .withUsername("test")
                    .withPassword("test");

    @DynamicPropertySource
    static void registerDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("app.jwt.secret",
                () -> "integration-test-secret-key-at-least-32-bytes-long-for-hs256");
    }
}
