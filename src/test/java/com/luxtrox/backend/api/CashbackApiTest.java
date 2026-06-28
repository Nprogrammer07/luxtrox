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
import static org.hamcrest.Matchers.greaterThanOrEqualTo;

/**
 * CashbackQueryService.getHistory()/getAllCashback() leen
 * tx.getPosition().getUser() (ambos FetchType.LAZY) -- este test
 * existe especificamente para confirmar con HTTP real, con una
 * transaccion de cashback REAL de por medio (no solo mocks), que esto
 * no repite el LazyInitializationException ya visto antes en este
 * proyecto (CustomUserPrincipal, ReferralController, UserService).
 */
class CashbackApiTest extends AbstractApiTest {

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
    void fullCashbackFlow_summaryHistoryMonthlyAndAdminListing_allWorkWithRealDistributedCashback() {
        String adminEmail = "cbadmin" + uniqueSuffix() + "@example.com";
        String adminToken = register(adminEmail);
        promoteToAdmin(adminEmail);

        String userEmail = "cbuser" + uniqueSuffix() + "@example.com";
        String userToken = register(userEmail);

        // ---------- comprar y confirmar una posicion Driver ----------
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

        // ---------- registrar y distribuir el rendimiento mensual ----------
        int uniqueYear = 2030 + (int) (System.nanoTime() % 5000); // unico por corrida -- no hay rollback entre tests
        String performanceId = given().header("Authorization", "Bearer " + adminToken)
                .contentType(ContentType.JSON)
                .body("""
                        {"month": 6, "year": %d, "percentage": 10.00}
                        """.formatted(uniqueYear))
                .when().post("/admin/cashback/monthly-performance")
                .then().statusCode(200).extract().path("id");

        given().header("Authorization", "Bearer " + adminToken)
                .when().post("/admin/cashback/monthly-performance/" + performanceId + "/distribute")
                .then().statusCode(204);

        // ---------- /cashback/summary ----------
        given().header("Authorization", "Bearer " + userToken)
                .when().get("/cashback/summary")
                .then().statusCode(200)
                .body("seminarsCount", equalTo(1));

        // ---------- /cashback/history -- aqui es donde se ejercita la cadena LAZY ----------
        given().header("Authorization", "Bearer " + userToken)
                .when().get("/cashback/history")
                .then().statusCode(200)
                .body("size()", greaterThanOrEqualTo(1))
                .body("[0].status", equalTo("PAID"))
                .body("[0].year", equalTo(uniqueYear));

        // ---------- /cashback/monthly ----------
        given().header("Authorization", "Bearer " + userToken)
                .when().get("/cashback/monthly")
                .then().statusCode(200)
                .body("size()", equalTo(6));

        // ---------- /admin/cashback -- misma cadena LAZY, vista de admin ----------
        given().header("Authorization", "Bearer " + adminToken)
                .when().get("/admin/cashback")
                .then().statusCode(200)
                .body("size()", greaterThanOrEqualTo(1));
    }
}
