package com.luxtrox.backend.api;

import com.luxtrox.backend.entity.Role;
import com.luxtrox.backend.entity.User;
import com.luxtrox.backend.integration.storage.SupabaseStorageClient;
import com.luxtrox.backend.repository.RoleRepository;
import com.luxtrox.backend.repository.UserRepository;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Flujo completo del pago manual (ver docs/domain-model.md S3.5),
 * todo por HTTP real: crear compra ALTERNATIVE -> admin aprueba ->
 * usuario sube comprobante (multipart) -> admin confirma -> la compra
 * queda CONFIRMED con la misma orquestacion que cualquier otro metodo
 * de pago.
 *
 * SupabaseStorageClient va con @MockBean a proposito: app.storage.endpoint
 * en AbstractApiTest apunta a "localhost:9999", un valor de relleno
 * que NUNCA tuvo un servidor real escuchando -- funciona en el resto
 * de la suite porque PurchaseService.generateInvoiceAndNotify()
 * atrapa cualquier fallo de storage/email sin romper el flujo
 * principal (a proposito: la factura es un "nice-to-have" posterior a
 * la confirmacion). uploadProof() es distinto: el comprobante de pago
 * ES el proposito del endpoint, asi que NO debe fallar en silencio --
 * por eso aqui se mockea el almacenamiento en vez de tragarse el
 * error como hace la factura.
 */
class AlternativePaymentApiTest extends AbstractApiTest {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;

    @MockBean
    private SupabaseStorageClient storageClient;

    @BeforeEach
    void stubStorage() {
        when(storageClient.uploadFile(any(), any(), any())).thenReturn("payment-proofs/fake-key");
        when(storageClient.downloadFile(any())).thenReturn("contenido-de-prueba".getBytes());
    }

    private String register(String email) {
        return given()
                .contentType(ContentType.JSON)
                .body("""
                        {"fullName": "Test User", "email": "%s", "phone": "+1", "password": "password123"}
                        """.formatted(email))
                .when().post("/auth/register")
                .then().statusCode(200)
                .extract().path("accessToken");
    }

    private void promoteToAdmin(String email) {
        User user = userRepository.findByEmail(email).orElseThrow();
        Role adminRole = roleRepository.findByName("ADMIN").orElseThrow();
        user.setRole(adminRole);
        userRepository.save(user);
    }

    @Test
    void fullManualPaymentFlow_endsWithPurchaseConfirmed() {
        String adminEmail = "altpayadmin" + uniqueSuffix() + "@example.com";
        String adminToken = register(adminEmail);
        promoteToAdmin(adminEmail);

        String userEmail = "altpayuser" + uniqueSuffix() + "@example.com";
        String userToken = register(userEmail);

        // ---------- crear la compra con paymentMethod=ALTERNATIVE ----------
        String purchaseId = given()
                .header("Authorization", "Bearer " + userToken)
                .contentType(ContentType.JSON)
                .body("""
                        {"planType": "ZENITH", "paymentMethod": "ALTERNATIVE"}
                        """)
        .when()
                .post("/purchases")
        .then()
                .statusCode(200)
                .extract().path("id");

        // La solicitud se crea SOLA -- el usuario ya puede consultarla.
        given().header("Authorization", "Bearer " + userToken)
                .when().get("/purchases/" + purchaseId + "/alternative-payment")
                .then().statusCode(200)
                .body("status", equalTo("REQUESTED"))
                .body("hasProofUploaded", equalTo(false));

        String requestId = given().header("Authorization", "Bearer " + adminToken)
                .when().get("/admin/alternative-payments?status=REQUESTED")
                .then().statusCode(200)
                .body("size()", equalTo(1))
                .extract().path("[0].id");

        // ---------- admin aprueba ----------
        given().header("Authorization", "Bearer " + adminToken)
                .when().post("/admin/alternative-payments/" + requestId + "/approve")
                .then().statusCode(200).body("status", equalTo("APPROVED"));

        // ---------- el usuario sube el comprobante ----------
        given().header("Authorization", "Bearer " + userToken)
                .multiPart("file", "comprobante.png", "contenido-de-prueba".getBytes(), "image/png")
        .when()
                .post("/purchases/" + purchaseId + "/alternative-payment/proof")
        .then()
                .statusCode(200)
                .body("status", equalTo("UNDER_REVIEW"))
                .body("hasProofUploaded", equalTo(true));

        // ---------- admin descarga el comprobante para revisarlo ----------
        byte[] downloaded = given().header("Authorization", "Bearer " + adminToken)
                .when().get("/admin/alternative-payments/" + requestId + "/proof")
                .then().statusCode(200)
                .extract().asByteArray();
        org.assertj.core.api.Assertions.assertThat(new String(downloaded)).isEqualTo("contenido-de-prueba");

        // ---------- admin confirma ----------
        given().header("Authorization", "Bearer " + adminToken)
                .when().post("/admin/alternative-payments/" + requestId + "/confirm")
                .then().statusCode(200).body("status", equalTo("CONFIRMED"));

        // La compra subyacente debe haber quedado CONFIRMED via la misma orquestacion de siempre.
        given().header("Authorization", "Bearer " + userToken)
                .when().get("/purchases")
                .then().statusCode(200)
                .body("[0].status", equalTo("CONFIRMED"));
    }

    @Test
    void rejectingTheProof_alsoRejectsTheUnderlyingPurchase() {
        String adminEmail = "altpayreject" + uniqueSuffix() + "@example.com";
        String adminToken = register(adminEmail);
        promoteToAdmin(adminEmail);

        String userEmail = "altpayrejuser" + uniqueSuffix() + "@example.com";
        String userToken = register(userEmail);

        String purchaseId = given().header("Authorization", "Bearer " + userToken)
                .contentType(ContentType.JSON)
                .body("""
                        {"planType": "ZENITH", "paymentMethod": "ALTERNATIVE"}
                        """)
                .when().post("/purchases")
                .then().statusCode(200).extract().path("id");

        String requestId = given().header("Authorization", "Bearer " + adminToken)
                .when().get("/admin/alternative-payments?status=REQUESTED")
                .then().statusCode(200).extract().path("[0].id");

        given().header("Authorization", "Bearer " + adminToken)
                .when().post("/admin/alternative-payments/" + requestId + "/approve")
                .then().statusCode(200);

        given().header("Authorization", "Bearer " + userToken)
                .multiPart("file", "comprobante.png", "x".getBytes(), "image/png")
                .when().post("/purchases/" + purchaseId + "/alternative-payment/proof")
                .then().statusCode(200);

        given().header("Authorization", "Bearer " + adminToken)
                .contentType(ContentType.JSON)
                .body("""
                        {"adminNotes": "El comprobante no corresponde al monto de la compra"}
                        """)
        .when()
                .post("/admin/alternative-payments/" + requestId + "/reject")
        .then()
                .statusCode(200)
                .body("status", equalTo("REJECTED"))
                .body("adminNotes", equalTo("El comprobante no corresponde al monto de la compra"));

        given().header("Authorization", "Bearer " + userToken)
                .when().get("/purchases")
                .then().statusCode(200)
                .body("[0].status", equalTo("REJECTED"));
    }
}
