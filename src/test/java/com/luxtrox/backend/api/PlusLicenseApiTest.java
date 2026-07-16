package com.luxtrox.backend.api;

import com.luxtrox.backend.entity.Role;
import com.luxtrox.backend.entity.User;
import com.luxtrox.backend.repository.RoleRepository;
import com.luxtrox.backend.repository.UserRepository;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Tests de integración para el plan Luxtrox Genius (interno: PLUS).
 * Cubre: compra ($89 pago único), confirmación, listado de licencias,
 * comisión de referido ($19 fijos) y descuento Zenith ($100).
 *
 * Para obtener un token admin: registrar usuario normal → promover
 * vía repositorio → re-login (el JWT nuevo ya lleva el rol ADMIN).
 */
class PlusLicenseApiTest extends AbstractApiTest {

    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;

    private String register(String email) {
        given().contentType(ContentType.JSON)
                .body("""
                        {"fullName":"Plus User","email":"%s","phone":"+1","password":"password123"}
                        """.formatted(email))
                .when().post("/auth/register")
                .then().statusCode(200);

        return given().contentType(ContentType.JSON)
                .body("""
                        {"email":"%s","password":"password123"}
                        """.formatted(email))
                .when().post("/auth/login")
                .then().statusCode(200)
                .extract().path("accessToken");
    }

    private String registerAdmin() {
        String email = "plusadmin" + uniqueSuffix() + "@example.com";
        register(email);
        User user = userRepository.findByEmail(email).orElseThrow();
        Role adminRole = roleRepository.findByName("ADMIN").orElseThrow();
        user.setRole(adminRole);
        userRepository.save(user);
        return given().contentType(ContentType.JSON)
                .body("""
                        {"email":"%s","password":"password123"}
                        """.formatted(email))
                .when().post("/auth/login")
                .then().statusCode(200)
                .extract().path("accessToken");
    }

    private String createPlusPurchase(String token) {
        return given().contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + token)
                .body("""
                        {"planType":"PLUS","paymentMethod":"ALTERNATIVE"}
                        """)
                .when().post("/purchases")
                .then().statusCode(200)
                .extract().path("id");
    }

    private void confirmPurchase(String purchaseId, String adminToken) {
        given().contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + adminToken)
                .when().post("/admin/purchases/" + purchaseId + "/confirm")
                .then().statusCode(200);
    }

    @Test
    void myPlusLicenses_emptyBeforeAnyPurchase() {
        String token = register("plus-empty" + uniqueSuffix() + "@example.com");

        given().header("Authorization", "Bearer " + token)
                .when().get("/purchases/plus-licenses")
                .then().statusCode(200)
                .body("$", hasSize(0));
    }

    @Test
    void myPlusLicenses_afterConfirmedPurchase_appearsActive() {
        String adminToken = registerAdmin();
        String token = register("plus-buy" + uniqueSuffix() + "@example.com");
        String purchaseId = createPlusPurchase(token);
        confirmPurchase(purchaseId, adminToken);

        given().header("Authorization", "Bearer " + token)
                .when().get("/purchases/plus-licenses")
                .then().statusCode(200)
                .body("$", hasSize(1))
                .body("[0].status", equalTo("active"))
                .body("[0].currentPeriodEnd", notNullValue());
    }

    @Test
    void plusPurchase_price_is89() {
        String adminToken = registerAdmin();
        String token = register("plus-price" + uniqueSuffix() + "@example.com");
        String purchaseId = createPlusPurchase(token);

        given().header("Authorization", "Bearer " + adminToken)
                .when().get("/admin/purchases/requests")
                .then().statusCode(200)
                .body("find { it.id == '" + purchaseId + "' }.totalAmount",
                        equalTo(89.0f));
    }

    @Test
    void zenithDiscount_withActivePlus_priceReducedBy100() {
        String adminToken = registerAdmin();
        String token = register("plus-disc" + uniqueSuffix() + "@example.com");

        confirmPurchase(createPlusPurchase(token), adminToken);

        String zenithId = given().contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + token)
                .body("""
                        {"planType":"ZENITH","paymentMethod":"ALTERNATIVE"}
                        """)
                .when().post("/purchases")
                .then().statusCode(200)
                .extract().path("id");

        given().header("Authorization", "Bearer " + adminToken)
                .when().get("/admin/purchases/requests")
                .then().statusCode(200)
                .body("find { it.id == '" + zenithId + "' }.totalAmount",
                        equalTo(2199.0f));
    }

    @Test
    void zenithDiscount_withoutPlus_fullPrice() {
        String adminToken = registerAdmin();
        String token = register("plus-nodis" + uniqueSuffix() + "@example.com");

        String zenithId = given().contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + token)
                .body("""
                        {"planType":"ZENITH","paymentMethod":"ALTERNATIVE"}
                        """)
                .when().post("/purchases")
                .then().statusCode(200)
                .extract().path("id");

        given().header("Authorization", "Bearer " + adminToken)
                .when().get("/admin/purchases/requests")
                .then().statusCode(200)
                .body("find { it.id == '" + zenithId + "' }.totalAmount",
                        equalTo(2299.0f));
    }

    @Test
    void plusReferral_referrerReceives19Usd() {
        String adminToken = registerAdmin();

        String referrerEmail = "plus-ref" + uniqueSuffix() + "@example.com";
        String referrerToken = register(referrerEmail);
        String referralCode = given().header("Authorization", "Bearer " + referrerToken)
                .when().get("/referrals/summary")
                .then().statusCode(200)
                .extract().path("code");

        String referredEmail = "plus-refr" + uniqueSuffix() + "@example.com";
        given().contentType(ContentType.JSON)
                .body("""
                        {"fullName":"Referred","email":"%s","phone":"+1",
                         "password":"password123","referralCode":"%s"}
                        """.formatted(referredEmail, referralCode))
                .when().post("/auth/register")
                .then().statusCode(200);

        String referredToken = given().contentType(ContentType.JSON)
                .body("""
                        {"email":"%s","password":"password123"}
                        """.formatted(referredEmail))
                .when().post("/auth/login")
                .then().statusCode(200)
                .extract().path("accessToken");

        confirmPurchase(createPlusPurchase(referredToken), adminToken);

        given().header("Authorization", "Bearer " + referrerToken)
                .when().get("/cashback/summary")
                .then().statusCode(200)
                .body("available", equalTo(19.0f));
    }
}
