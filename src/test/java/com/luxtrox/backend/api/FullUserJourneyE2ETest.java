package com.luxtrox.backend.api;

import com.luxtrox.backend.entity.Role;
import com.luxtrox.backend.entity.User;
import com.luxtrox.backend.repository.RoleRepository;
import com.luxtrox.backend.repository.UserRepository;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Flujo completo de un usuario real, de punta a punta, todo por HTTP
 * real (no llamadas directas a un service): registro -> referido ->
 * compra -> confirmacion de admin -> comision de referido -> reparto
 * de rendimiento mensual -> retiro -> aprobacion -> pago.
 *
 * El estado FINAL se verifica via repositorio (no hay un endpoint
 * "mi perfil" que expongan el saldo todavia) -- el FLUJO en si, sin
 * embargo, corre integramente via la API real, igual que lo haria un
 * usuario de verdad.
 *
 * Las compras se crean con paymentMethod=ALTERNATIVE (no CRYPTO) por
 * la misma razon que en NowPaymentsWebhookApiTest: evitar una llamada
 * de red real a NOWPayments durante el test.
 */
class FullUserJourneyE2ETest extends AbstractApiTest {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;

    private String register(String email, String referralCode) {
        String body = referralCode == null
                ? """
                  {"fullName": "Test User", "email": "%s", "phone": "+1", "password": "password123"}
                  """.formatted(email)
                : """
                  {"fullName": "Test User", "email": "%s", "phone": "+1", "password": "password123", "referralCode": "%s"}
                  """.formatted(email, referralCode);

        return given().contentType(ContentType.JSON).body(body)
                .when().post("/auth/register")
                .then().statusCode(200)
                .extract().path("accessToken");
    }

    private String referralCodeOf(String email) {
        return userRepository.findByEmail(email).orElseThrow().getReferralCode();
    }

    private void promoteToAdmin(String email) {
        User user = userRepository.findByEmail(email).orElseThrow();
        Role adminRole = roleRepository.findByName("ADMIN").orElseThrow();
        user.setRole(adminRole);
        userRepository.save(user);
    }

    private String createDriverPurchase(String token) {
        return given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body("""
                        {"planType": "DRIVER", "packageQuantity": 1, "paymentMethod": "ALTERNATIVE"}
                        """)
        .when()
                .post("/purchases")
        .then()
                .statusCode(200)
                .extract().path("id");
    }

    private void confirmPurchase(String adminToken, String purchaseId) {
        given().header("Authorization", "Bearer " + adminToken)
                .when().post("/admin/purchases/" + purchaseId + "/confirm")
                .then().statusCode(200);
    }

    private BigDecimal balanceOf(String email) {
        return userRepository.findByEmail(email).orElseThrow().getAvailableBalance();
    }

    @Test
    void fullJourney_referral_monthlyDistribution_andWithdrawal() {
        String adminEmail = "e2eadmin" + uniqueSuffix() + "@example.com";
        String referrerEmail = "e2eref" + uniqueSuffix() + "@example.com";
        String referredEmail = "e2eed" + uniqueSuffix() + "@example.com";

        // ---------- Setup ----------
        String adminToken = register(adminEmail, null);
        promoteToAdmin(adminEmail);

        String referrerToken = register(referrerEmail, null);
        String referrerCode = referralCodeOf(referrerEmail);

        // ---------- Fase A: el referente compra y se confirma ----------
        String referrerPurchaseId = createDriverPurchase(referrerToken);
        confirmPurchase(adminToken, referrerPurchaseId);

        assertThat(balanceOf(referrerEmail)).isEqualByComparingTo("0.00"); // su propia compra no le da cashback inmediato

        // ---------- Fase B: el referido se registra CON el codigo, compra, y se confirma ----------
        String referredToken = register(referredEmail, referrerCode);
        String referredPurchaseId = createDriverPurchase(referredToken);
        confirmPurchase(adminToken, referredPurchaseId);

        // Comision: 9% de 1099 = 98.91, acreditada DIRECTO al balance del
        // referente (sin restricciones de plan -- ya no se avanza posicion).
        assertThat(balanceOf(referrerEmail)).isEqualByComparingTo("98.91");

        // ---------- Fase C: rendimiento mensual, se registra y se distribuye ----------
        int uniqueYear = 2030 + (int) (System.nanoTime() % 5000); // unico por corrida -- no hay rollback entre tests
        String performanceId = given()
                .header("Authorization", "Bearer " + adminToken)
                .contentType(ContentType.JSON)
                .body("""
                        {"month": 6, "year": %d, "percentage": 10.00}
                        """.formatted(uniqueYear))
        .when()
                .post("/admin/cashback/monthly-performance")
        .then()
                .statusCode(200)
                .extract().path("id");

        given().header("Authorization", "Bearer " + adminToken)
                .when().post("/admin/cashback/monthly-performance/" + performanceId + "/distribute")
                .then().statusCode(204);

        // Referente: +10% de 1099 = 109.90 sobre SU propia posicion -> 98.91 + 109.90 = 208.81
        assertThat(balanceOf(referrerEmail)).isEqualByComparingTo("208.81");
        // Referido: +10% de 1099 = 109.90 sobre la suya
        assertThat(balanceOf(referredEmail)).isEqualByComparingTo("109.90");

        // ---------- Fase D: el referente retira, se aprueba, se paga ----------
        String withdrawalId = given()
                .header("Authorization", "Bearer " + referrerToken)
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "amount": 150.00,
                          "fullName": "Test User",
                          "email": "%s",
                          "phone": "+1",
                          "blockchainNetwork": "TRC20",
                          "walletAddress": "wallet-e2e-123"
                        }
                        """.formatted(referrerEmail))
        .when()
                .post("/withdrawals/crypto")
        .then()
                .statusCode(200)
                .body("status", org.hamcrest.Matchers.equalTo("REQUESTED"))
                .extract().path("id");

        // El monto se descuenta DE INMEDIATO al solicitar.
        assertThat(balanceOf(referrerEmail)).isEqualByComparingTo("58.81"); // 208.81 - 150.00

        given().header("Authorization", "Bearer " + adminToken)
                .when().post("/admin/withdrawals/" + withdrawalId + "/approve")
                .then().statusCode(200).body("status", org.hamcrest.Matchers.equalTo("APPROVED"));

        given().header("Authorization", "Bearer " + adminToken)
                .when().post("/admin/withdrawals/" + withdrawalId + "/mark-paid")
                .then().statusCode(200).body("status", org.hamcrest.Matchers.equalTo("PAID"));

        // Pagar no devuelve nada -- el saldo sigue como quedo al solicitar.
        assertThat(balanceOf(referrerEmail)).isEqualByComparingTo("58.81");
    }
}
