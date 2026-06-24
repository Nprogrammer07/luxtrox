package com.luxtrox.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Smoke test de Fase 4: si el contexto de Spring carga, significa que
 * las 15 entidades calzan con el esquema real (hibernate.ddl-auto =
 * validate) y que la conexion a Supabase funciona.
 *
 * La suite de testing completa (unitarios con Mockito, integracion
 * con Testcontainers, API con RestAssured, E2E, estres) es la Fase 8
 * del proyecto -- esto es deliberadamente minimo por ahora.
 *
 * Requiere el perfil "local" con application-local.yml configurado
 * (ver docs/running-locally.md). Correr con:
 *   mvn test -Dspring-boot.run.profiles=local
 */
@SpringBootTest
@ActiveProfiles("local")
class LuxtroxBackendApplicationTests {

    @Test
    void contextLoads() {
        // Si este test pasa, Hibernate ya valido las 15 entidades
        // contra las tablas reales de Supabase al arrancar el contexto.
    }
}
