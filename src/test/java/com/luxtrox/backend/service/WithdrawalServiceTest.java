package com.luxtrox.backend.service;

import com.luxtrox.backend.entity.Role;
import com.luxtrox.backend.entity.User;
import com.luxtrox.backend.entity.WithdrawalRequest;
import com.luxtrox.backend.entity.enums.BankAccountType;
import com.luxtrox.backend.entity.enums.WithdrawalStatus;
import com.luxtrox.backend.exception.BusinessRuleException;
import com.luxtrox.backend.repository.AbstractIntegrationTest;
import com.luxtrox.backend.repository.RoleRepository;
import com.luxtrox.backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Prueba el algoritmo de retiros de docs/domain-model.md §4.3: el
 * descuento inmediato del saldo al solicitar, y que solo el rechazo
 * (no la aprobacion ni el pago) lo devuelve.
 */
class WithdrawalServiceTest extends AbstractIntegrationTest {

    @Autowired
    private WithdrawalService withdrawalService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;

    private User createUserWithBalance(String email, String balance) {
        Role role = roleRepository.findByName("USER").orElseThrow();
        User user = new User("Test", email, "+1", "hash", role, "REF" + email.hashCode());
        user.setAvailableBalance(new BigDecimal(balance));
        return userRepository.save(user);
    }

    private User createAdmin(String email) {
        Role role = roleRepository.findByName("ADMIN").orElseThrow();
        return userRepository.save(new User("Admin", email, "+1", "hash", role, "ADM" + email.hashCode()));
    }

    @Test
    void requestRejectsAmountBelowFiftyDollars() {
        User user = createUserWithBalance("low@example.com", "1000.00");

        assertThrows(BusinessRuleException.class, () -> withdrawalService.requestCrypto(
                user, new BigDecimal("49.99"), "Carlos", "carlos@x.com", "+1", "TRC20", "wallet123"));
    }

    @Test
    void requestRejectsAmountAboveAvailableBalance() {
        User user = createUserWithBalance("poor@example.com", "100.00");

        assertThrows(BusinessRuleException.class, () -> withdrawalService.requestCrypto(
                user, new BigDecimal("200.00"), "Carlos", "carlos@x.com", "+1", "TRC20", "wallet123"));
    }

    @Test
    void requestDeductsTheBalanceImmediately() {
        User user = createUserWithBalance("deduct@example.com", "1000.00");

        withdrawalService.requestCrypto(user, new BigDecimal("300.00"),
                "Carlos", "carlos@x.com", "+1", "TRC20", "wallet123");

        User refreshed = userRepository.findById(user.getId()).orElseThrow();
        assertThat(refreshed.getAvailableBalance()).isEqualByComparingTo("700.00");
    }

    @Test
    void rejectingARequestRefundsTheBalance() {
        User user = createUserWithBalance("refund@example.com", "1000.00");
        User admin = createAdmin("admin1@example.com");

        WithdrawalRequest request = withdrawalService.requestCrypto(user, new BigDecimal("300.00"),
                "Carlos", "carlos@x.com", "+1", "TRC20", "wallet123");

        withdrawalService.reject(request.getId(), admin, "Datos invalidos");

        User refreshed = userRepository.findById(user.getId()).orElseThrow();
        assertThat(refreshed.getAvailableBalance()).isEqualByComparingTo("1000.00");
    }

    @Test
    void approvingThenMarkingPaidNeverRefundsTheBalance() {
        User user = createUserWithBalance("paid@example.com", "1000.00");
        User admin = createAdmin("admin2@example.com");

        WithdrawalRequest request = withdrawalService.requestBank(user, new BigDecimal("300.00"),
                "Carlos", "carlos@x.com", "+1", "Colombia", "Bancolombia",
                BankAccountType.SAVINGS, "123456", "Carlos Mendoza", "1000111222");

        withdrawalService.approve(request.getId(), admin);
        withdrawalService.markPaid(request.getId(), admin);

        User refreshed = userRepository.findById(user.getId()).orElseThrow();
        assertThat(refreshed.getAvailableBalance()).isEqualByComparingTo("700.00"); // sigue descontado
    }

    @Test
    void cannotApproveARequestThatIsNotInRequestedStatus() {
        User user = createUserWithBalance("twice@example.com", "1000.00");
        User admin = createAdmin("admin3@example.com");

        WithdrawalRequest request = withdrawalService.requestCrypto(user, new BigDecimal("300.00"),
                "Carlos", "carlos@x.com", "+1", "TRC20", "wallet123");
        withdrawalService.approve(request.getId(), admin);

        assertThrows(BusinessRuleException.class, () -> withdrawalService.approve(request.getId(), admin));
    }

    @Test
    void cannotMarkPaidBeforeApproval() {
        User user = createUserWithBalance("skip@example.com", "1000.00");
        User admin = createAdmin("admin4@example.com");

        WithdrawalRequest request = withdrawalService.requestCrypto(user, new BigDecimal("300.00"),
                "Carlos", "carlos@x.com", "+1", "TRC20", "wallet123");

        assertThrows(BusinessRuleException.class, () -> withdrawalService.markPaid(request.getId(), admin));
    }

    @Test
    void finalStatusesAreCorrectAfterFullApprovalFlow() {
        User user = createUserWithBalance("flow@example.com", "1000.00");
        User admin = createAdmin("admin5@example.com");

        WithdrawalRequest request = withdrawalService.requestCrypto(user, new BigDecimal("300.00"),
                "Carlos", "carlos@x.com", "+1", "TRC20", "wallet123");
        assertThat(request.getStatus()).isEqualTo(WithdrawalStatus.REQUESTED);

        withdrawalService.approve(request.getId(), admin);
        withdrawalService.markPaid(request.getId(), admin);
    }
}
