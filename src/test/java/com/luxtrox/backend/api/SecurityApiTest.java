package com.luxtrox.backend.api;

import com.luxtrox.backend.entity.Role;
import com.luxtrox.backend.entity.User;
import com.luxtrox.backend.repository.RoleRepository;
import com.luxtrox.backend.repository.UserRepository;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

/**
 * Prueba la seguridad a nivel HTTP real: que las rutas protegidas
 * realmente bloqueen sin token, que un token valido SI deje pasar, y
 * que /admin/** realmente exija el rol ADMIN -- nada de esto lo cubre
 * un test de servicio, porque los filtros de Spring Security ni
 * siquiera se activan al llamar un service directamente.
 */
class SecurityApiTest extends AbstractApiTest {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;

    private String registerAndGetAccessToken(String email) {
        return given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "fullName": "Test User",
                          "email": "%s",
                          "phone": "+1",
                          "password": "password123"
                        }
                        """.formatted(email))
        .when()
                .post("/auth/register")
        .then()
                .statusCode(200)
                .extract().path("accessToken");
    }

    /** Promueve el usuario recien registrado a ADMIN -- el registro publico SIEMPRE asigna USER. */
    private void promoteToAdmin(String email) {
        User user = userRepository.findByEmail(email).orElseThrow();
        Role adminRole = roleRepository.findByName("ADMIN").orElseThrow();
        user.setRole(adminRole);
        userRepository.save(user);
    }

    @Test
    void protectedEndpoint_withoutAnyToken_returns401() {
        given()
        .when()
                .get("/purchases")
        .then()
                .statusCode(401);
    }

    @Test
    void protectedEndpoint_withMalformedBearerToken_returns401() {
        given()
                .header("Authorization", "Bearer esto-no-es-un-jwt-valido")
        .when()
                .get("/purchases")
        .then()
                .statusCode(401);
    }

    @Test
    void protectedEndpoint_withValidToken_isAllowedThrough() {
        String email = "secaccess" + uniqueSuffix() + "@example.com";
        String token = registerAndGetAccessToken(email);

        given()
                .header("Authorization", "Bearer " + token)
        .when()
                .get("/purchases")
        .then()
                .statusCode(200); // lista vacia, pero PASA la seguridad
    }

    @Test
    void adminRoute_withRegularUser_returns403() {
        String email = "secregular" + uniqueSuffix() + "@example.com";
        String token = registerAndGetAccessToken(email); // queda con rol USER, sin promover

        given()
                .header("Authorization", "Bearer " + token)
        .when()
                .post("/admin/purchases/" + java.util.UUID.randomUUID() + "/confirm")
        .then()
                .statusCode(403);
    }

    @Test
    void adminRoute_withAdminUser_passesSecurity() {
        String email = "secadmin" + uniqueSuffix() + "@example.com";
        String token = registerAndGetAccessToken(email);
        promoteToAdmin(email);

        // Un UUID que no existe -- lo que importa aqui es que la
        // seguridad LO DEJA PASAR (404 del controller, no 403 del
        // filtro de seguridad). Probar el flujo de negocio completo
        // de confirmacion ya esta cubierto por PurchaseServiceTest.
        given()
                .header("Authorization", "Bearer " + token)
        .when()
                .post("/admin/purchases/" + java.util.UUID.randomUUID() + "/confirm")
        .then()
                .statusCode(404)
                .body("error", equalTo("NOT_FOUND"));
    }

    @Test
    void authEndpoints_areAccessibleWithoutAnyToken() {
        // /auth/** debe ser publico -- si esto diera 401, nadie podria registrarse nunca.
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"email": "no-existe@example.com", "password": "cualquiera"}
                        """)
        .when()
                .post("/auth/login")
        .then()
                .statusCode(401) // rechazado por CREDENCIALES invalidas, no por falta de auth
                .body("error", equalTo("INVALID_CREDENTIALS"));
    }
}