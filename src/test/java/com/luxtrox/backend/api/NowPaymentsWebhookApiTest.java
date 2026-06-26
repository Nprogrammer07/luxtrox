package com.luxtrox.backend.api;

import com.luxtrox.backend.integration.nowpayments.NowPaymentsSignatureVerifier;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

/**
 * Prueba de punta a punta, por HTTP real: registro -> login -> crear
 * compra -> callback IPN firmado -> verificar el estado final. Es la
 * unica prueba que ejercita el controller del webhook con una firma
 * HMAC REAL (no mockeada) sobre un servidor real.
 *
 * NOTA: las compras de prueba se crean con paymentMethod=ALTERNATIVE,
 * no CRYPTO -- usar CRYPTO disparia una llamada de red real a
 * NOWPayments (con una API key falsa, fallaria). El controller del
 * webhook no valida el metodo de pago de la compra que confirma, asi
 * que esto prueba la mecanica de firma+confirmacion igual de bien,
 * sin tocar la red. En el mundo real esta combinacion no pasaria.
 */
class NowPaymentsWebhookApiTest extends AbstractApiTest {

    @Autowired
    private NowPaymentsSignatureVerifier signatureVerifier;

    private String registerAndGetToken(String email) {
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

    private String createPendingZenithPurchase(String token) {
        return given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body("""
                        {"planType": "ZENITH", "paymentMethod": "ALTERNATIVE"}
                        """)
        .when()
                .post("/purchases")
        .then()
                .statusCode(200)
                .extract().path("id");
    }

    private String purchaseStatus(String token) {
        return given()
                .header("Authorization", "Bearer " + token)
        .when()
                .get("/purchases")
        .then()
                .statusCode(200)
                .extract().path("[0].status");
    }

    private String sign(String rawJson) {
        try {
            return signatureVerifier.computeSignature(rawJson, TEST_NOWPAYMENTS_IPN_SECRET);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void validSignatureWithFinishedStatus_confirmsTheReferencedPurchase() {
        String email = "ipnok" + uniqueSuffix() + "@example.com";
        String token = registerAndGetToken(email);
        String purchaseId = createPendingZenithPurchase(token);

        String payload = """
                {"payment_id":"123456","payment_status":"finished","order_id":"%s","price_amount":"2299.00","price_currency":"usd"}
                """.formatted(purchaseId).strip();

        given()
                .contentType(ContentType.JSON)
                .header("x-nowpayments-sig", sign(payload))
                .body(payload)
        .when()
                .post("/webhooks/nowpayments/ipn")
        .then()
                .statusCode(200);

        assertThat(purchaseStatus(token)).isEqualTo("CONFIRMED");
    }

    @Test
    void invalidSignature_returns401AndNeverConfirmsThePurchase() {
        String email = "ipnbadsig" + uniqueSuffix() + "@example.com";
        String token = registerAndGetToken(email);
        String purchaseId = createPendingZenithPurchase(token);

        String payload = """
                {"payment_id":"123456","payment_status":"finished","order_id":"%s","price_amount":"2299.00","price_currency":"usd"}
                """.formatted(purchaseId).strip();

        given()
                .contentType(ContentType.JSON)
                .header("x-nowpayments-sig", "firma-deliberadamente-incorrecta")
                .body(payload)
        .when()
                .post("/webhooks/nowpayments/ipn")
        .then()
                .statusCode(401);

        assertThat(purchaseStatus(token)).isEqualTo("PENDING");
    }

    @Test
    void validSignatureButIntermediateStatus_acknowledgesWithoutConfirming() {
        String email = "ipnpending" + uniqueSuffix() + "@example.com";
        String token = registerAndGetToken(email);
        String purchaseId = createPendingZenithPurchase(token);

        String payload = """
                {"payment_id":"123456","payment_status":"confirming","order_id":"%s","price_amount":"2299.00","price_currency":"usd"}
                """.formatted(purchaseId).strip();

        given()
                .contentType(ContentType.JSON)
                .header("x-nowpayments-sig", sign(payload))
                .body(payload)
        .when()
                .post("/webhooks/nowpayments/ipn")
        .then()
                .statusCode(200); // se reconoce el callback, no es un error

        assertThat(purchaseStatus(token)).isEqualTo("PENDING"); // pero NO se confirmo
    }

    @Test
    void validSignatureButNonExistentPurchase_returns404() {
        String payload = """
                {"payment_id":"123456","payment_status":"finished","order_id":"%s","price_amount":"100.00","price_currency":"usd"}
                """.formatted(java.util.UUID.randomUUID()).strip();

        given()
                .contentType(ContentType.JSON)
                .header("x-nowpayments-sig", sign(payload))
                .body(payload)
        .when()
                .post("/webhooks/nowpayments/ipn")
        .then()
                .statusCode(404)
                .body("error", equalTo("NOT_FOUND"));
    }
}