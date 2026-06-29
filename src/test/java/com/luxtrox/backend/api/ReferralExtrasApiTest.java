package com.luxtrox.backend.api;

import com.luxtrox.backend.repository.UserRepository;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

/**
 * GET /referrals/summary, /bonuses, /validate/{code} -- ninguno
 * existia antes de esta integracion. bonuses() toca
 * CashbackTransaction.sourceReferral (FetchType.LAZY), de ahi el
 * enfoque en confirmar por HTTP real que no repite el
 * LazyInitializationException ya visto varias veces en este proyecto.
 */
class ReferralExtrasApiTest extends AbstractApiTest {

    @Autowired
    private UserRepository userRepository;

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

    @Test
    void summary_withNoReferralsYet_returnsZeroedOut() {
        String email = "norefs" + uniqueSuffix() + "@example.com";
        String token = register(email, null);

        given().header("Authorization", "Bearer " + token)
                .when().get("/referrals/summary")
                .then().statusCode(200)
                .body("totalReferrals", equalTo(0))
                .body("activeReferrals", equalTo(0))
                .body("pendingBonus", equalTo(0));
    }

    @Test
    void summary_afterOneReferral_countsItAsActive() {
        String referrerEmail = "summaryreferrer" + uniqueSuffix() + "@example.com";
        String referrerToken = register(referrerEmail, null);
        String referrerCode = userRepository.findByEmail(referrerEmail).orElseThrow().getReferralCode();

        register("summaryreferred" + uniqueSuffix() + "@example.com", referrerCode);

        given().header("Authorization", "Bearer " + referrerToken)
                .when().get("/referrals/summary")
                .then().statusCode(200)
                .body("totalReferrals", equalTo(1))
                .body("activeReferrals", equalTo(1)); // PENDING_PURCHASE todavia -- el referido no ha comprado
    }

    @Test
    void bonuses_emptyHistory_doesNotThrow_returnsEmptyList() {
        String email = "nobonuses" + uniqueSuffix() + "@example.com";
        String token = register(email, null);

        given().header("Authorization", "Bearer " + token)
                .when().get("/referrals/bonuses")
                .then().statusCode(200)
                .body("size()", equalTo(0));
    }

    @Test
    void validate_existingCode_returnsValidWithOwnerName() {
        String email = "validatable" + uniqueSuffix() + "@example.com";
        register(email, null);
        String code = userRepository.findByEmail(email).orElseThrow().getReferralCode();

        String anyToken = register("validator" + uniqueSuffix() + "@example.com", null);

        given().header("Authorization", "Bearer " + anyToken)
                .when().get("/referrals/validate/" + code)
                .then().statusCode(200)
                .body("valid", equalTo(true))
                .body("ownerName", equalTo("Test User"));
    }

    @Test
    void validate_unknownCode_returnsInvalid() {
        String anyToken = register("validator2" + uniqueSuffix() + "@example.com", null);

        given().header("Authorization", "Bearer " + anyToken)
                .when().get("/referrals/validate/NOEXISTE99")
                .then().statusCode(200)
                .body("valid", equalTo(false));
    }
}