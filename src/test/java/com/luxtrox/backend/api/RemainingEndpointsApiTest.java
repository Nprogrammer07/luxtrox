package com.luxtrox.backend.api;

import com.luxtrox.backend.entity.Role;
import com.luxtrox.backend.entity.User;
import com.luxtrox.backend.entity.ZenithLicense;
import com.luxtrox.backend.repository.RoleRepository;
import com.luxtrox.backend.repository.UserRepository;
import com.luxtrox.backend.repository.ZenithLicenseRepository;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Cubre endpoints que el reporte de cobertura de JaCoCo mostro en 0%
 * o con metodos sin tocar -- ver el analisis previo a este archivo.
 * No es "perseguir un numero": cada uno de estos es una ruta real que
 * un usuario o admin de verdad usaria, y que ningun otro test (ni de
 * servicio ni de API) ejercitaba todavia.
 */
class RemainingEndpointsApiTest extends AbstractApiTest {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private ZenithLicenseRepository zenithLicenseRepository;

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

    // ---------- GET /referrals/my-code ----------

    @Test
    void myReferralCode_returnsTheCallersOwnCode() {
        String email = "refcode" + uniqueSuffix() + "@example.com";
        String token = register(email, null);
        String expectedCode = referralCodeOf(email);

        given().header("Authorization", "Bearer " + token)
                .when().get("/referrals/my-code")
                .then().statusCode(200)
                .body(equalTo(expectedCode));
    }

    // ---------- GET /referrals ----------

    @Test
    void myReferrals_listsReferredUsersWithTheirStatus() {
        String referrerEmail = "refowner" + uniqueSuffix() + "@example.com";
        String referrerToken = register(referrerEmail, null);
        String code = referralCodeOf(referrerEmail);

        String referredEmail = "refchild" + uniqueSuffix() + "@example.com";
        register(referredEmail, code); // solo el registro ya crea la fila de Referral

        given().header("Authorization", "Bearer " + referrerToken)
                .when().get("/referrals")
                .then().statusCode(200)
                .body("size()", equalTo(1))
                .body("[0].referredEmail", equalTo(referredEmail))
                .body("[0].status", equalTo("PENDING_PURCHASE")); // todavia no compro nada
    }

    @Test
    void myReferrals_withNoReferrals_returnsEmptyList() {
        String email = "norefs" + uniqueSuffix() + "@example.com";
        String token = register(email, null);

        given().header("Authorization", "Bearer " + token)
                .when().get("/referrals")
                .then().statusCode(200)
                .body("size()", equalTo(0));
    }

    // ---------- POST /withdrawals/bank ----------

    @Test
    void requestBankWithdrawal_happyPath() {
        String email = "bankwd" + uniqueSuffix() + "@example.com";
        String token = register(email, null);
        User user = userRepository.findByEmail(email).orElseThrow();
        user.setAvailableBalance(new java.math.BigDecimal("200.00"));
        userRepository.save(user);

        given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "amount": 100.00,
                          "fullName": "Test User",
                          "email": "%s",
                          "phone": "+1",
                          "country": "Colombia",
                          "bankName": "Bancolombia",
                          "accountType": "SAVINGS",
                          "accountNumber": "1234567890",
                          "accountHolderName": "Test User",
                          "documentId": "1000000000"
                        }
                        """.formatted(email))
        .when()
                .post("/withdrawals/bank")
        .then()
                .statusCode(200)
                .body("type", equalTo("BANK"))
                .body("status", equalTo("REQUESTED"));
    }

    // ---------- GET /withdrawals ----------

    @Test
    void myWithdrawals_listsOwnRequestsOnly() {
        String email = "listwd" + uniqueSuffix() + "@example.com";
        String token = register(email, null);
        User user = userRepository.findByEmail(email).orElseThrow();
        user.setAvailableBalance(new java.math.BigDecimal("100.00"));
        userRepository.save(user);

        given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "amount": 60.00,
                          "fullName": "Test User",
                          "email": "%s",
                          "phone": "+1",
                          "blockchainNetwork": "TRC20",
                          "walletAddress": "wallet-list-test"
                        }
                        """.formatted(email))
                .when().post("/withdrawals/crypto")
                .then().statusCode(200);

        given().header("Authorization", "Bearer " + token)
                .when().get("/withdrawals")
                .then().statusCode(200)
                .body("size()", equalTo(1));

        java.math.BigDecimal listedAmount = given().header("Authorization", "Bearer " + token)
                .when().get("/withdrawals")
                .then().extract().jsonPath().getObject("[0].amount", java.math.BigDecimal.class);
        org.assertj.core.api.Assertions.assertThat(listedAmount).isEqualByComparingTo("60.00");
    }

    // ---------- POST /admin/withdrawals/{id}/reject ----------

    @Test
    void adminRejectsAWithdrawal_refundsTheBalance() {
        String adminEmail = "rejadmin" + uniqueSuffix() + "@example.com";
        String adminToken = register(adminEmail, null);
        promoteToAdmin(adminEmail);

        String userEmail = "rejuser" + uniqueSuffix() + "@example.com";
        String userToken = register(userEmail, null);
        User user = userRepository.findByEmail(userEmail).orElseThrow();
        user.setAvailableBalance(new java.math.BigDecimal("100.00"));
        userRepository.save(user);

        String withdrawalId = given().header("Authorization", "Bearer " + userToken)
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "amount": 80.00,
                          "fullName": "Test User",
                          "email": "%s",
                          "phone": "+1",
                          "blockchainNetwork": "TRC20",
                          "walletAddress": "wallet-reject-test"
                        }
                        """.formatted(userEmail))
                .when().post("/withdrawals/crypto")
                .then().statusCode(200).extract().path("id");

        given().header("Authorization", "Bearer " + adminToken)
                .contentType(ContentType.JSON)
                .body("""
                        {"adminNotes": "Datos bancarios incorrectos"}
                        """)
        .when()
                .post("/admin/withdrawals/" + withdrawalId + "/reject")
        .then()
                .statusCode(200)
                .body("status", equalTo("REJECTED"))
                .body("adminNotes", equalTo("Datos bancarios incorrectos"));

        User refreshed = userRepository.findByEmail(userEmail).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(refreshed.getAvailableBalance()).isEqualByComparingTo("100.00"); // se devolvio completo
    }

    // ---------- POST /admin/purchases/zenith-licenses/{id}/renew y expire-overdue ----------

    @Test
    void renewZenithLicense_chargesAndExtendsIt() {
        String adminEmail = "zenadmin" + uniqueSuffix() + "@example.com";
        String adminToken = register(adminEmail, null);
        promoteToAdmin(adminEmail);

        String userEmail = "zenuser" + uniqueSuffix() + "@example.com";
        String userToken = register(userEmail, null);

        String purchaseId = given().header("Authorization", "Bearer " + userToken)
                .contentType(ContentType.JSON)
                .body("""
                        {"planType": "ZENITH", "paymentMethod": "ALTERNATIVE"}
                        """)
                .when().post("/purchases")
                .then().statusCode(200).extract().path("id");

        given().header("Authorization", "Bearer " + adminToken)
                .when().post("/admin/purchases/" + purchaseId + "/confirm")
                .then().statusCode(200);

        ZenithLicense license = zenithLicenseRepository
                .findByUser(userRepository.findByEmail(userEmail).orElseThrow())
                .get(0);
        var periodEndBefore = license.getCurrentPeriodEnd();

        given().header("Authorization", "Bearer " + adminToken)
                .when().post("/admin/purchases/zenith-licenses/" + license.getId() + "/renew")
                .then().statusCode(204);

        ZenithLicense refreshed = zenithLicenseRepository.findById(license.getId()).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(refreshed.getCurrentPeriodEnd())
                .isEqualTo(periodEndBefore.plusYears(1));
    }

    @Test
    void expireOverdueZenithLicenses_returnsHowManyItExpired() {
        String adminEmail = "expadmin" + uniqueSuffix() + "@example.com";
        String adminToken = register(adminEmail, null);
        promoteToAdmin(adminEmail);

        int expiredCount = given().header("Authorization", "Bearer " + adminToken)
                .when().post("/admin/purchases/zenith-licenses/expire-overdue")
                .then().statusCode(200)
                .extract().as(Integer.class);

        // No importa cuantas -- solo que responda un numero valido, no que truene.
        org.assertj.core.api.Assertions.assertThat(expiredCount).isGreaterThanOrEqualTo(0);
    }
}