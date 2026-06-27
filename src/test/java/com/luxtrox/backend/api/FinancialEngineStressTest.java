package com.luxtrox.backend.api;

import com.luxtrox.backend.entity.Role;
import com.luxtrox.backend.entity.User;
import com.luxtrox.backend.repository.RoleRepository;
import com.luxtrox.backend.repository.UserRepository;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.IntStream;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Carga concurrente real sobre el punto mas sensible del motor
 * financiero: modificaciones simultaneas al saldo de un usuario y al
 * reparto de un rendimiento mensual. Antes de esta fase, ni User ni
 * MonthlyPerformance tenian bloqueo optimista (@Version) -- esto
 * verifica que el que se agrego (ver migracion V19) realmente evita
 * los dos problemas que existian:
 *   1. Update perdido: dos retiros concurrentes del mismo usuario
 *      podian ambos pasar la validacion de saldo antes de que
 *      cualquiera guardara, perdiendo una de las dos restas.
 *   2. Doble pago: dos llamadas casi simultaneas a distribute() para
 *      el mismo rendimiento podian ambas pasar el chequeo de
 *      idempotencia antes de que cualquiera marcara appliedAt,
 *      repartiendo el dinero dos veces.
 *
 * Lo que se prueba NO es "que todas las solicitudes validas tengan
 * exito" (no hay reintento automatico -- el que pierde la carrera
 * recibe 409 limpio, ver GlobalExceptionHandler) sino que el
 * resultado final sea matematicamente consistente sin importar
 * cuantas ganaron la carrera.
 */
class FinancialEngineStressTest extends AbstractApiTest {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;

    private String registerAndGetToken(String email) {
        return given()
                .contentType(ContentType.JSON)
                .body("""
                        {"fullName": "Test User", "email": "%s", "phone": "+1", "password": "password123"}
                        """.formatted(email))
        .when()
                .post("/auth/register")
        .then()
                .statusCode(200)
                .extract().path("accessToken");
    }

    private void setBalanceDirectly(String email, BigDecimal balance) {
        User user = userRepository.findByEmail(email).orElseThrow();
        user.setAvailableBalance(balance);
        userRepository.save(user);
    }

    private BigDecimal currentBalance(String email) {
        return userRepository.findByEmail(email).orElseThrow().getAvailableBalance();
    }

    private void promoteToAdmin(String email) {
        User user = userRepository.findByEmail(email).orElseThrow();
        Role adminRole = roleRepository.findByName("ADMIN").orElseThrow();
        user.setRole(adminRole);
        userRepository.save(user);
    }

    /**
     * Dispara N solicitudes de retiro REALMENTE concurrentes (todos
     * los hilos esperan en el mismo CountDownLatch y se liberan
     * juntos) para maximizar la chance de que de verdad se solapen en
     * el tiempo, en vez de ejecutarse una tras otra por casualidad.
     */
    private List<Integer> fireConcurrentWithdrawals(String token, String email, int count, BigDecimal amountEach)
            throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(count);
        CountDownLatch readyLatch = new CountDownLatch(count);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<Integer>> futures = IntStream.range(0, count).mapToObj(i -> pool.submit(() -> {
            readyLatch.countDown();
            startLatch.await();
            return given()
                    .header("Authorization", "Bearer " + token)
                    .contentType(ContentType.JSON)
                    .body("""
                            {
                              "amount": %s,
                              "fullName": "Test User",
                              "email": "%s",
                              "phone": "+1",
                              "blockchainNetwork": "TRC20",
                              "walletAddress": "wallet-stress-%d"
                            }
                            """.formatted(amountEach, email, i))
            .when()
                    .post("/withdrawals/crypto")
            .then()
                    .extract().statusCode();
        })).toList();

        readyLatch.await(10, TimeUnit.SECONDS);
        startLatch.countDown();

        List<Integer> statusCodes = futures.stream().map(f -> {
            try {
                return f.get(15, TimeUnit.SECONDS);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).toList();

        pool.shutdown();
        return statusCodes;
    }

    @Test
    void concurrentWithdrawals_neverLoseAnUpdate_andNeverGoNegative() throws InterruptedException {
        String email = "stress" + uniqueSuffix() + "@example.com";
        String token = registerAndGetToken(email);
        setBalanceDirectly(email, new BigDecimal("500.00"));

        // 10 solicitudes de $100 cada una contra un saldo de $500 --
        // como maximo 5 deberian poder tener exito mathematically.
        List<Integer> results = fireConcurrentWithdrawals(token, email, 10, new BigDecimal("100.00"));

        long successCount = results.stream().filter(code -> code == 200).count();
        long conflictOrRejectedCount = results.stream().filter(code -> code == 409 || code == 422).count();

        assertThat(successCount + conflictOrRejectedCount).isEqualTo(10); // ninguna respuesta inesperada
        assertThat(successCount).isLessThanOrEqualTo(5); // nunca mas exitos de los que el saldo permite

        BigDecimal expectedFinalBalance = new BigDecimal("500.00")
                .subtract(new BigDecimal("100.00").multiply(BigDecimal.valueOf(successCount)));
        BigDecimal actualFinalBalance = currentBalance(email);

        // ESTA es la aserción central: el saldo final debe coincidir
        // EXACTO con la cuenta de cuantas solicitudes tuvieron exito
        // -- si hubiera un update perdido, estos dos numeros
        // divergerian.
        assertThat(actualFinalBalance).isEqualByComparingTo(expectedFinalBalance);
        assertThat(actualFinalBalance.compareTo(BigDecimal.ZERO)).isGreaterThanOrEqualTo(0); // nunca negativo
    }

    @Test
    void concurrentDistributeCalls_forTheSamePerformance_neverDoublePay() throws Exception {
        String adminEmail = "stressadmin" + uniqueSuffix() + "@example.com";
        String userEmail = "stressuser" + uniqueSuffix() + "@example.com";
        String adminToken = registerAndGetToken(adminEmail);
        promoteToAdmin(adminEmail);
        String userToken = registerAndGetToken(userEmail);

        String purchaseId = given()
                .header("Authorization", "Bearer " + userToken)
                .contentType(ContentType.JSON)
                .body("""
                        {"planType": "DRIVER", "packageQuantity": 1, "paymentMethod": "ALTERNATIVE"}
                        """)
        .when().post("/purchases")
        .then().statusCode(200).extract().path("id");

        given().header("Authorization", "Bearer " + adminToken)
                .when().post("/admin/purchases/" + purchaseId + "/confirm")
                .then().statusCode(200);

        int uniqueYear = 2040 + (int) (System.nanoTime() % 5000);
        String performanceId = given()
                .header("Authorization", "Bearer " + adminToken)
                .contentType(ContentType.JSON)
                .body("""
                        {"month": 6, "year": %d, "percentage": 10.00}
                        """.formatted(uniqueYear))
        .when().post("/admin/cashback/monthly-performance")
        .then().statusCode(200).extract().path("id");

        // 5 llamadas REALMENTE concurrentes a distribute() para el MISMO performanceId.
        int concurrentCalls = 5;
        ExecutorService pool = Executors.newFixedThreadPool(concurrentCalls);
        CountDownLatch readyLatch = new CountDownLatch(concurrentCalls);
        CountDownLatch startLatch = new CountDownLatch(1);

        List<Future<Integer>> futures = IntStream.range(0, concurrentCalls).mapToObj(i -> pool.submit(() -> {
            readyLatch.countDown();
            startLatch.await();
            return given().header("Authorization", "Bearer " + adminToken)
                    .when().post("/admin/cashback/monthly-performance/" + performanceId + "/distribute")
                    .then().extract().statusCode();
        })).toList();

        readyLatch.await(10, TimeUnit.SECONDS);
        startLatch.countDown();
        for (Future<Integer> f : futures) {
            f.get(15, TimeUnit.SECONDS);
        }
        pool.shutdown();

        // 1099 * 10% = 109.90 -- sin importar cuantas de las 5 llamadas
        // "tuvieron exito" (204), el dinero solo debe haberse repartido UNA vez.
        BigDecimal balance = userRepository.findByEmail(userEmail).orElseThrow().getAvailableBalance();
        assertThat(balance).isEqualByComparingTo("109.90");
    }
}
