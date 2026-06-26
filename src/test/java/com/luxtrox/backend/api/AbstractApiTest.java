package com.luxtrox.backend.api;

import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base para tests de API de punta a punta -- HTTP real (no llamadas
 * directas a un service) contra un servidor Spring Boot real en un
 * puerto aleatorio, con Postgres real via Testcontainers (mismo
 * patron que AbstractIntegrationTest de Fase 4, pero con
 * webEnvironment=RANDOM_PORT en vez de MOCK).
 *
 * IMPORTANTE -- a diferencia de AbstractIntegrationTest, aqui NO hay
 * @Transactional ni rollback automatico: el servidor HTTP procesa
 * cada request en su propio hilo, separado del hilo del metodo de
 * test, asi que una transaccion abierta en el hilo del test no
 * envuelve nada de lo que pasa via HTTP. Los datos que cada test cree
 * quedan committeados de verdad en la base del contenedor. Por eso
 * cada test que crea un usuario debe generar un email/codigo unico
 * (ver uniqueSuffix()) -- nunca asumir una base vacia entre tests de
 * la misma clase.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class AbstractApiTest {

    protected static final String TEST_NOWPAYMENTS_IPN_SECRET = "test-nowpayments-ipn-secret";

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("luxtrox_test")
                    .withUsername("test")
                    .withPassword("test");

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("app.jwt.secret",
                () -> "integration-test-secret-key-at-least-32-bytes-long-for-hs256");

        registry.add("app.nowpayments.api-key", () -> "test-nowpayments-api-key");
        registry.add("app.nowpayments.ipn-secret", () -> TEST_NOWPAYMENTS_IPN_SECRET);
        registry.add("app.nowpayments.ipn-callback-url", () -> "http://localhost:8080/webhooks/nowpayments/ipn");
        registry.add("app.resend.api-key", () -> "test-resend-api-key");
        registry.add("app.storage.endpoint", () -> "http://localhost:9999/storage/v1/s3");
        registry.add("app.storage.region", () -> "us-east-1");
        registry.add("app.storage.access-key-id", () -> "test-access-key");
        registry.add("app.storage.secret-access-key", () -> "test-secret-key");
    }

    @LocalServerPort
    private int port;

    @BeforeEach
    void configureRestAssured() {
        RestAssured.baseURI = "http://localhost";
        RestAssured.port = port;
    }

    /** Sufijo aleatorio para emails/codigos -- evita colisiones entre tests (no hay rollback, ver arriba). */
    protected static String uniqueSuffix() {
        return java.util.UUID.randomUUID().toString().substring(0, 8);
    }
}