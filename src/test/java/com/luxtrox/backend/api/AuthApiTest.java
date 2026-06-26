package com.luxtrox.backend.api;

import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Prueba el flujo de autenticacion via HTTP real -- esto verifica algo
 * que ningun test de servicio puede: que el routing, la
 * (de)serializacion JSON, las validaciones de @Valid, y el formato de
 * respuesta de error (ver GlobalExceptionHandler) funcionan juntos
 * correctamente sobre el protocolo real.
 */
class AuthApiTest extends AbstractApiTest {

    @Test
    void register_happyPath_returns200WithTokensAndUserSummary() {
        String email = "carlos" + uniqueSuffix() + "@example.com";

        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "fullName": "Carlos Mendoza",
                          "email": "%s",
                          "phone": "+1234567890",
                          "password": "password123"
                        }
                        """.formatted(email))
        .when()
                .post("/auth/register")
        .then()
                .statusCode(200)
                .body("accessToken", not(emptyOrNullString()))
                .body("refreshToken", not(emptyOrNullString()))
                .body("tokenType", equalTo("Bearer"))
                .body("user.email", equalTo(email))
                .body("user.referralCode", not(emptyOrNullString()));
    }

    @Test
    void register_duplicateEmail_returns422() {
        String email = "duplicado" + uniqueSuffix() + "@example.com";
        String body = """
                {
                  "fullName": "Carlos",
                  "email": "%s",
                  "phone": "+1",
                  "password": "password123"
                }
                """.formatted(email);

        given().contentType(ContentType.JSON).body(body).when().post("/auth/register")
                .then().statusCode(200);

        // La segunda vez con el MISMO email debe rechazarse.
        given().contentType(ContentType.JSON).body(body).when().post("/auth/register")
                .then()
                .statusCode(422)
                .body("error", equalTo("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    void register_invalidEmail_returns400WithFieldErrors() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "fullName": "Carlos",
                          "email": "esto-no-es-un-email",
                          "phone": "+1",
                          "password": "password123"
                        }
                        """)
        .when()
                .post("/auth/register")
        .then()
                .statusCode(400)
                .body("error", equalTo("VALIDATION_ERROR"))
                .body("fields.email", not(emptyOrNullString()));
    }

    @Test
    void register_passwordTooShort_returns400() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "fullName": "Carlos",
                          "email": "test%s@example.com",
                          "phone": "+1",
                          "password": "abc"
                        }
                        """.formatted(uniqueSuffix()))
        .when()
                .post("/auth/register")
        .then()
                .statusCode(400)
                .body("fields.password", not(emptyOrNullString()));
    }

    @Test
    void login_correctCredentials_returns200WithTokens() {
        String email = "login" + uniqueSuffix() + "@example.com";
        registerUser(email, "password123");

        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"email": "%s", "password": "password123"}
                        """.formatted(email))
        .when()
                .post("/auth/login")
        .then()
                .statusCode(200)
                .body("accessToken", not(emptyOrNullString()))
                .body("user.email", equalTo(email));
    }

    @Test
    void login_wrongPassword_returns401() {
        String email = "loginfail" + uniqueSuffix() + "@example.com";
        registerUser(email, "password123");

        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"email": "%s", "password": "contrasena-incorrecta"}
                        """.formatted(email))
        .when()
                .post("/auth/login")
        .then()
                .statusCode(401)
                .body("error", equalTo("INVALID_CREDENTIALS"));
    }

    @Test
    void login_nonExistentEmail_returns401() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"email": "no-existe-nadie-con-este-correo@example.com", "password": "cualquiera123"}
                        """)
        .when()
                .post("/auth/login")
        .then()
                .statusCode(401);
    }

    @Test
    void refresh_validToken_returnsNewTokenPair() {
        String email = "refresh" + uniqueSuffix() + "@example.com";
        String refreshToken = registerUser(email, "password123").path("refreshToken");

        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"refreshToken": "%s"}
                        """.formatted(refreshToken))
        .when()
                .post("/auth/refresh")
        .then()
                .statusCode(200)
                .body("accessToken", not(emptyOrNullString()))
                .body("refreshToken", not(equalTo(refreshToken))); // debe ser uno NUEVO (rotacion)
    }

    @Test
    void refresh_garbageToken_returns401() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"refreshToken": "esto-no-es-un-token-valido"}
                        """)
        .when()
                .post("/auth/refresh")
        .then()
                .statusCode(401)
                .body("error", equalTo("INVALID_REFRESH_TOKEN"));
    }

    @Test
    void logout_thenReusingTheSameRefreshToken_isRejected() {
        String email = "logout" + uniqueSuffix() + "@example.com";
        String refreshToken = registerUser(email, "password123").path("refreshToken");

        given().contentType(ContentType.JSON)
                .body("""
                        {"refreshToken": "%s"}
                        """.formatted(refreshToken))
                .when().post("/auth/logout")
                .then().statusCode(204);

        // El mismo token, usado de nuevo, ya no debe servir.
        given().contentType(ContentType.JSON)
                .body("""
                        {"refreshToken": "%s"}
                        """.formatted(refreshToken))
                .when().post("/auth/refresh")
                .then().statusCode(401);
    }

    private io.restassured.response.Response registerUser(String email, String password) {
        return given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "fullName": "Test User",
                          "email": "%s",
                          "phone": "+1",
                          "password": "%s"
                        }
                        """.formatted(email, password))
        .when()
                .post("/auth/register")
        .then()
                .statusCode(200)
                .extract().response();
    }
}