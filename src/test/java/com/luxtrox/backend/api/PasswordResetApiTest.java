package com.luxtrox.backend.api;

import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

/**
 * Cubre los endpoints de recuperacion de contrasena agregados a
 * AuthController, para que JaCoCo los incluya en el calculo de
 * cobertura y no falle el umbral del 90%.
 *
 * POST /auth/forgot-password  -- solicitar reset (siempre 204, no
 *   revela si el email existe o no)
 * POST /auth/reset-password   -- aplicar nuevo password con token
 *   valido (204) o invalido (422)
 */
class PasswordResetApiTest extends AbstractApiTest {

    private String registerAndGetEmail() {
        String email = "reset" + uniqueSuffix() + "@example.com";
        given().contentType(ContentType.JSON)
                .body("""
                        {"fullName":"Reset User","email":"%s","phone":"+1","password":"password123"}
                        """.formatted(email))
                .when().post("/auth/register")
                .then().statusCode(200);
        return email;
    }

    // ---------- forgot-password ----------

    @Test
    void forgotPassword_existingEmail_returns204WithoutRevealingItExists() {
        String email = registerAndGetEmail();

        given().contentType(ContentType.JSON)
                .body("""
                        {"email":"%s"}
                        """.formatted(email))
                .when().post("/auth/forgot-password")
                .then().statusCode(204);
    }

    @Test
    void forgotPassword_nonExistingEmail_alsoReturns204_preventingEnumeration() {
        given().contentType(ContentType.JSON)
                .body("""
                        {"email":"nonexistent@example.com"}
                        """)
                .when().post("/auth/forgot-password")
                .then().statusCode(204);
    }

    @Test
    void forgotPassword_invalidEmailFormat_returns400() {
        given().contentType(ContentType.JSON)
                .body("""
                        {"email":"not-an-email"}
                        """)
                .when().post("/auth/forgot-password")
                .then().statusCode(400);
    }

    // ---------- reset-password ----------

    @Test
    void resetPassword_invalidToken_returns422() {
        given().contentType(ContentType.JSON)
                .body("""
                        {"token":"00000000-0000-0000-0000-000000000000","newPassword":"newPassword123"}
                        """)
                .when().post("/auth/reset-password")
                .then().statusCode(422);
    }

    @Test
    void resetPassword_passwordTooShort_returns400() {
        given().contentType(ContentType.JSON)
                .body("""
                        {"token":"00000000-0000-0000-0000-000000000000","newPassword":"short"}
                        """)
                .when().post("/auth/reset-password")
                .then().statusCode(400);
    }
}
