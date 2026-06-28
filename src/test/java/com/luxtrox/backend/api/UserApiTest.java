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
import static org.hamcrest.Matchers.nullValue;

/**
 * UserService.toResponse() lee user.getReferredBy().getReferralCode()
 * -- referredBy es FetchType.LAZY. Este test existe especificamente
 * para confirmar con HTTP real que esto NO repite el
 * LazyInitializationException ya visto antes en este proyecto
 * (CustomUserPrincipal, ReferralController) -- aqui se evito
 * colocando @Transactional en el SERVICE (no en el controller), de
 * forma que el mapeo a DTO corre DENTRO de la misma transaccion que
 * el acceso lazy, no despues de que ya cerro.
 */
class UserApiTest extends AbstractApiTest {

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

    @Test
    void me_withNoReferrer_returnsNullReferredBy() {
        String email = "noref" + uniqueSuffix() + "@example.com";
        String token = register(email, null);

        given().header("Authorization", "Bearer " + token)
                .when().get("/users/me")
                .then().statusCode(200)
                .body("email", equalTo(email))
                .body("referredBy", nullValue())
                .body("seminarsCount", equalTo(0))
                .body("status", equalTo("ACTIVE"));
    }

    @Test
    void me_withReferrer_returnsReferrersCode_doesNotThrowLazyInitException() {
        String referrerEmail = "referrer" + uniqueSuffix() + "@example.com";
        register(referrerEmail, null);
        String referrerCode = userRepository.findByEmail(referrerEmail).orElseThrow().getReferralCode();

        String referredEmail = "referred" + uniqueSuffix() + "@example.com";
        String referredToken = register(referredEmail, referrerCode);

        given().header("Authorization", "Bearer " + referredToken)
                .when().get("/users/me")
                .then().statusCode(200)
                .body("referredBy", equalTo(referrerCode));
    }

    @Test
    void updateMe_changesNameAndPhone() {
        String email = "update" + uniqueSuffix() + "@example.com";
        String token = register(email, null);

        given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body("""
                        {"fullName": "Nombre Actualizado", "phone": "+5555"}
                        """)
                .when().put("/users/me")
                .then().statusCode(200)
                .body("name", equalTo("Nombre Actualizado"));
    }

    @Test
    void adminEndpoints_listAndChangeStatus_workEndToEnd() {
        String adminEmail = "useradmin" + uniqueSuffix() + "@example.com";
        String adminToken = register(adminEmail, null);
        promoteToAdmin(adminEmail);

        String targetEmail = "target" + uniqueSuffix() + "@example.com";
        register(targetEmail, null);

        String targetId = given().header("Authorization", "Bearer " + adminToken)
                .queryParam("search", targetEmail)
                .when().get("/admin/users")
                .then().statusCode(200)
                .body("size()", equalTo(1))
                .extract().path("[0].id");

        given().header("Authorization", "Bearer " + adminToken)
                .when().get("/admin/users/" + targetId)
                .then().statusCode(200)
                .body("email", equalTo(targetEmail));

        given().header("Authorization", "Bearer " + adminToken)
                .contentType(ContentType.JSON)
                .body("""
                        {"status": "SUSPENDED"}
                        """)
                .when().patch("/admin/users/" + targetId + "/status")
                .then().statusCode(200)
                .body("status", equalTo("SUSPENDED"));
    }

    @Test
    void regularUser_cannotAccessAdminUserEndpoints() {
        String email = "regular" + uniqueSuffix() + "@example.com";
        String token = register(email, null);

        given().header("Authorization", "Bearer " + token)
                .when().get("/admin/users")
                .then().statusCode(403);
    }
}