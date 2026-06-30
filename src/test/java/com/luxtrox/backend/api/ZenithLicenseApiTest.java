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
 * GET /purchases/zenith-licenses -- no existia ningun endpoint que
 * expusiera esto al usuario antes de esta integracion. Sin cadena
 * LAZY que cuidar aqui (ver ZenithService.listMyLicenses()), pero el
 * test igual valida el flujo completo via HTTP real: comprar Zenith,
 * confirmar como admin, y que la licencia aparezca en el listado.
 */
class ZenithLicenseApiTest extends AbstractApiTest {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;

    private String register(String email) {
        return given().contentType(ContentType.JSON)
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
    void myZenithLicenses_emptyBeforeAnyPurchase() {
        String token = register("nozenith" + uniqueSuffix() + "@example.com");

        given().header("Authorization", "Bearer " + token)
                .when().get("/purchases/zenith-licenses")
                .then().statusCode(200)
                .body("size()", equalTo(0));
    }

    @Test
    void myZenithLicenses_afterConfirmedPurchase_appearsActive() {
        String adminEmail = "zenithadmin" + uniqueSuffix() + "@example.com";
        String adminToken = register(adminEmail);
        promoteToAdmin(adminEmail);

        String userToken = register("zenithuser" + uniqueSuffix() + "@example.com");

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

        given().header("Authorization", "Bearer " + userToken)
                .when().get("/purchases/zenith-licenses")
                .then().statusCode(200)
                .body("size()", equalTo(1))
                .body("[0].status", equalTo("ACTIVE"));
    }
}
