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
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;

/**
 * Valida los 3 endpoints de admin que no existian antes de esta
 * integracion (GET /admin/withdrawals, GET /admin/purchases,
 * GET /admin/referrals) -- los tres tocan asociaciones FetchType.LAZY
 * (user de WithdrawalRequest/InvestmentPosition, referrer/referred de
 * Referral), asi que este test existe especificamente para confirmar
 * por HTTP real que no repiten el LazyInitializationException ya
 * visto varias veces antes en este proyecto.
 */
class AdminListingsApiTest extends AbstractApiTest {

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

    private void promoteToAdmin(String email) {
        User user = userRepository.findByEmail(email).orElseThrow();
        Role adminRole = roleRepository.findByName("ADMIN").orElseThrow();
        user.setRole(adminRole);
        userRepository.save(user);
    }

    private void giveBalance(String email, String amount) {
        User user = userRepository.findByEmail(email).orElseThrow();
        user.setAvailableBalance(new BigDecimal(amount));
        userRepository.save(user);
    }

    @Test
    void adminWithdrawalsListing_cryptoAndBank_resolvesLazyChainsWithoutError() {
        String adminEmail = "listadmin" + uniqueSuffix() + "@example.com";
        String adminToken = register(adminEmail, null);
        promoteToAdmin(adminEmail);

        String cryptoUserEmail = "cryptouser" + uniqueSuffix() + "@example.com";
        String cryptoToken = register(cryptoUserEmail, null);
        giveBalance(cryptoUserEmail, "200.00");

        given().header("Authorization", "Bearer " + cryptoToken)
                .contentType(ContentType.JSON)
                .body("""
                        {"amount": 100.00, "fullName": "Cripto User", "email": "%s", "phone": "+1",
                         "blockchainNetwork": "TRC20", "walletAddress": "wallet-abc"}
                        """.formatted(cryptoUserEmail))
                .when().post("/withdrawals/crypto")
                .then().statusCode(200);

        String bankUserEmail = "bankuser" + uniqueSuffix() + "@example.com";
        String bankToken = register(bankUserEmail, null);
        giveBalance(bankUserEmail, "200.00");

        given().header("Authorization", "Bearer " + bankToken)
                .contentType(ContentType.JSON)
                .body("""
                        {"amount": 100.00, "fullName": "Bank User", "email": "%s", "phone": "+1",
                         "country": "Colombia", "bankName": "Bancolombia", "accountType": "SAVINGS",
                         "accountNumber": "111222333", "accountHolderName": "Bank User", "documentId": "100200300"}
                        """.formatted(bankUserEmail))
                .when().post("/withdrawals/bank")
                .then().statusCode(200);

        given().header("Authorization", "Bearer " + adminToken)
                .when().get("/admin/withdrawals")
                .then().statusCode(200)
                .body("size()", greaterThanOrEqualTo(2))
                .body("findAll { it.walletAddress == 'wallet-abc' }.size()", equalTo(1))
                .body("findAll { it.userName == 'Bank User' && it.walletAddress == null }.size()", equalTo(1));
    }

    @Test
    void adminSeminarsListing_resolvesLazyUserChain_withoutError() {
        String adminEmail = "semadmin" + uniqueSuffix() + "@example.com";
        String adminToken = register(adminEmail, null);
        promoteToAdmin(adminEmail);

        String userEmail = "semuser" + uniqueSuffix() + "@example.com";
        String userToken = register(userEmail, null);

        String purchaseId = given().header("Authorization", "Bearer " + userToken)
                .contentType(ContentType.JSON)
                .body("""
                        {"planType": "DRIVER", "packageQuantity": 1, "paymentMethod": "ALTERNATIVE"}
                        """)
                .when().post("/purchases")
                .then().statusCode(200).extract().path("id");

        given().header("Authorization", "Bearer " + adminToken)
                .when().post("/admin/purchases/" + purchaseId + "/confirm")
                .then().statusCode(200);

        given().header("Authorization", "Bearer " + adminToken)
                .when().get("/admin/purchases")
                .then().statusCode(200)
                .body("size()", greaterThanOrEqualTo(1))
                .body("[0].status", equalTo("active"));
    }

    @Test
    void adminReferralsListing_resolvesLazyReferrerAndReferredChains_withoutError() {
        String adminEmail = "refadmin" + uniqueSuffix() + "@example.com";
        String adminToken = register(adminEmail, null);
        promoteToAdmin(adminEmail);

        String referrerEmail = "referrer" + uniqueSuffix() + "@example.com";
        register(referrerEmail, null);
        String referrerCode = userRepository.findByEmail(referrerEmail).orElseThrow().getReferralCode();

        String referredEmail = "referred" + uniqueSuffix() + "@example.com";
        register(referredEmail, referrerCode);

        given().header("Authorization", "Bearer " + adminToken)
                .when().get("/admin/referrals")
                .then().statusCode(200)
                .body("size()", greaterThanOrEqualTo(1))
                .body("findAll { it.referredUserEmail == '%s' }.size()".formatted(referredEmail), equalTo(1))
                .body("findAll { it.referredUserEmail == '%s' }[0].status".formatted(referredEmail), equalTo("active"));
    }

    @Test
    void myPositionsListing_resolvesLazyUserChain_withoutError() {
        String userEmail = "myposuser" + uniqueSuffix() + "@example.com";
        String userToken = register(userEmail, null);
        String adminEmail = "myposadmin" + uniqueSuffix() + "@example.com";
        String adminToken = register(adminEmail, null);
        promoteToAdmin(adminEmail);

        String purchaseId = given().header("Authorization", "Bearer " + userToken)
                .contentType(ContentType.JSON)
                .body("""
                        {"planType": "DRIVER", "packageQuantity": 1, "paymentMethod": "ALTERNATIVE"}
                        """)
                .when().post("/purchases")
                .then().statusCode(200).extract().path("id");

        given().header("Authorization", "Bearer " + adminToken)
                .when().post("/admin/purchases/" + purchaseId + "/confirm")
                .then().statusCode(200);

        given().header("Authorization", "Bearer " + userToken)
                .when().get("/purchases/positions")
                .then().statusCode(200)
                .body("size()", equalTo(1))
                .body("[0].status", equalTo("active"));
    }
}