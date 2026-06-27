package com.luxtrox.backend.unit.service;

import com.luxtrox.backend.entity.Role;
import com.luxtrox.backend.entity.User;
import com.luxtrox.backend.entity.WithdrawalRequest;
import com.luxtrox.backend.entity.enums.BankAccountType;
import com.luxtrox.backend.entity.enums.WithdrawalStatus;
import com.luxtrox.backend.entity.enums.WithdrawalType;
import com.luxtrox.backend.exception.BusinessRuleException;
import com.luxtrox.backend.exception.ResourceNotFoundException;
import com.luxtrox.backend.repository.BankWithdrawalDetailRepository;
import com.luxtrox.backend.repository.CryptoWithdrawalDetailRepository;
import com.luxtrox.backend.repository.UserRepository;
import com.luxtrox.backend.repository.WithdrawalRequestRepository;
import com.luxtrox.backend.service.AuditService;
import com.luxtrox.backend.service.NotificationEmailService;
import com.luxtrox.backend.service.WithdrawalService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unitario puro con Mockito -- complementa a WithdrawalServiceTest
 * (Testcontainers, Fase 6).
 */
@ExtendWith(MockitoExtension.class)
class WithdrawalServiceUnitTest {

    @Mock private WithdrawalRequestRepository withdrawalRequestRepository;
    @Mock private CryptoWithdrawalDetailRepository cryptoDetailRepository;
    @Mock private BankWithdrawalDetailRepository bankDetailRepository;
    @Mock private UserRepository userRepository;
    @Mock private AuditService auditService;
    @Mock private NotificationEmailService notificationEmailService;

    private WithdrawalService service;
    private MeterRegistry meterRegistry;
    private User user;
    private User admin;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        service = new WithdrawalService(withdrawalRequestRepository, cryptoDetailRepository,
                bankDetailRepository, userRepository, auditService, notificationEmailService, meterRegistry);

        Role role = new Role("USER", "Usuario estandar");
        user = new User("Carlos", "carlos@example.com", "+1", "hash", role, "CARLOS01");
        setId(user, UUID.randomUUID());
        user.setAvailableBalance(new BigDecimal("500.00"));

        Role adminRole = new Role("ADMIN", "Administrador");
        admin = new User("Admin", "admin@example.com", "+1", "hash", adminRole, "ADMIN001");
        setId(admin, UUID.randomUUID());

        lenient().when(withdrawalRequestRepository.save(any(WithdrawalRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private void setId(Object entity, UUID id) {
        try {
            var field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private WithdrawalRequest requestedWithdrawal(User owner, BigDecimal amount) {
        WithdrawalRequest request = new WithdrawalRequest(owner, WithdrawalType.CRYPTO, amount);
        setId(request, UUID.randomUUID());
        return request;
    }

    // ---------- requestCrypto() / requestBank() (via createBaseRequest) ----------

    @Test
    void requestCrypto_happyPath_deductsBalanceImmediately() {
        WithdrawalRequest result = service.requestCrypto(user, new BigDecimal("100.00"),
                "Carlos", "carlos@example.com", "+1", "TRC20", "wallet123");

        assertThat(result.getStatus()).isEqualTo(WithdrawalStatus.REQUESTED);
        assertThat(user.getAvailableBalance()).isEqualByComparingTo("400.00"); // 500 - 100
        verify(cryptoDetailRepository).save(any());
        verify(userRepository).save(user);

        assertThat(meterRegistry.get("luxtrox.withdrawals.requested").tag("type", "CRYPTO")
                .counter().count()).isEqualTo(1.0);
    }

    @Test
    void requestBank_happyPath_deductsBalanceImmediately() {
        WithdrawalRequest result = service.requestBank(user, new BigDecimal("100.00"),
                "Carlos", "carlos@example.com", "+1", "Colombia", "Bancolombia",
                BankAccountType.SAVINGS, "1234567890", "Carlos", "1000000000");

        assertThat(result.getStatus()).isEqualTo(WithdrawalStatus.REQUESTED);
        assertThat(user.getAvailableBalance()).isEqualByComparingTo("400.00");
        verify(bankDetailRepository).save(any());
    }

    @Test
    void request_belowMinimum_throwsAndNeverTouchesBalance() {
        assertThrows(BusinessRuleException.class, () -> service.requestCrypto(user, new BigDecimal("49.99"),
                "Carlos", "carlos@example.com", "+1", "TRC20", "wallet123"));

        assertThat(user.getAvailableBalance()).isEqualByComparingTo("500.00"); // sin cambios
        verifyNoInteractions(cryptoDetailRepository, userRepository, withdrawalRequestRepository);
    }

    @Test
    void request_exactlyAtMinimum_succeeds() {
        WithdrawalRequest result = service.requestCrypto(user, new BigDecimal("50.00"),
                "Carlos", "carlos@example.com", "+1", "TRC20", "wallet123");
        assertThat(result).isNotNull();
    }

    @Test
    void request_aboveAvailableBalance_throwsAndNeverTouchesBalance() {
        assertThrows(BusinessRuleException.class, () -> service.requestCrypto(user, new BigDecimal("500.01"),
                "Carlos", "carlos@example.com", "+1", "TRC20", "wallet123"));

        assertThat(user.getAvailableBalance()).isEqualByComparingTo("500.00");
    }

    @Test
    void request_exactlyAtAvailableBalance_succeeds() {
        WithdrawalRequest result = service.requestCrypto(user, new BigDecimal("500.00"),
                "Carlos", "carlos@example.com", "+1", "TRC20", "wallet123");
        assertThat(result).isNotNull();
        assertThat(user.getAvailableBalance()).isEqualByComparingTo("0.00");
    }

    // ---------- approve() ----------

    @Test
    void approve_happyPath_movesToApprovedAndNotifies() {
        WithdrawalRequest request = requestedWithdrawal(user, new BigDecimal("100.00"));
        when(withdrawalRequestRepository.findById(request.getId())).thenReturn(Optional.of(request));

        WithdrawalRequest result = service.approve(request.getId(), admin);

        assertThat(result.getStatus()).isEqualTo(WithdrawalStatus.APPROVED);
        assertThat(result.getProcessedByAdmin()).isEqualTo(admin);
        verify(notificationEmailService).sendWithdrawalStatusChangedEmail(request, "Aprobado");
    }

    @Test
    void approve_notInRequestedStatus_throws() {
        WithdrawalRequest request = requestedWithdrawal(user, new BigDecimal("100.00"));
        request.setStatus(WithdrawalStatus.PAID);
        when(withdrawalRequestRepository.findById(request.getId())).thenReturn(Optional.of(request));

        assertThrows(BusinessRuleException.class, () -> service.approve(request.getId(), admin));
    }

    @Test
    void approve_unknownId_throwsResourceNotFound() {
        UUID id = UUID.randomUUID();
        when(withdrawalRequestRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.approve(id, admin));
    }

    @Test
    void approve_emailFailure_stillApprovesAndAudits() {
        WithdrawalRequest request = requestedWithdrawal(user, new BigDecimal("100.00"));
        when(withdrawalRequestRepository.findById(request.getId())).thenReturn(Optional.of(request));
        doThrow(new RuntimeException("Resend caido")).when(notificationEmailService)
                .sendWithdrawalStatusChangedEmail(any(), any());

        WithdrawalRequest result = service.approve(request.getId(), admin);

        assertThat(result.getStatus()).isEqualTo(WithdrawalStatus.APPROVED); // SI se aprobo
        verify(auditService).recordSystemAction(eq("WithdrawalRequest"), any(), eq("EMAIL_NOTIFICATION_FAILED"), any(), any());
    }

    // ---------- reject() ----------

    @Test
    void reject_happyPath_refundsBalance() {
        WithdrawalRequest request = requestedWithdrawal(user, new BigDecimal("100.00"));
        when(withdrawalRequestRepository.findById(request.getId())).thenReturn(Optional.of(request));

        WithdrawalRequest result = service.reject(request.getId(), admin, "Datos incorrectos");

        assertThat(result.getStatus()).isEqualTo(WithdrawalStatus.REJECTED);
        assertThat(result.getAdminNotes()).isEqualTo("Datos incorrectos");
        assertThat(user.getAvailableBalance()).isEqualByComparingTo("600.00"); // 500 + 100 devuelto
        verify(notificationEmailService).sendWithdrawalStatusChangedEmail(request, "Rechazado");
    }

    @Test
    void reject_notInRequestedStatus_throwsAndNeverRefunds() {
        WithdrawalRequest request = requestedWithdrawal(user, new BigDecimal("100.00"));
        request.setStatus(WithdrawalStatus.APPROVED);
        when(withdrawalRequestRepository.findById(request.getId())).thenReturn(Optional.of(request));

        assertThrows(BusinessRuleException.class, () -> service.reject(request.getId(), admin, "notas"));
        assertThat(user.getAvailableBalance()).isEqualByComparingTo("500.00"); // sin cambios
    }

    // ---------- markPaid() ----------

    @Test
    void markPaid_happyPath_movesToPaid() {
        WithdrawalRequest request = requestedWithdrawal(user, new BigDecimal("100.00"));
        request.setStatus(WithdrawalStatus.APPROVED);
        when(withdrawalRequestRepository.findById(request.getId())).thenReturn(Optional.of(request));

        WithdrawalRequest result = service.markPaid(request.getId(), admin);

        assertThat(result.getStatus()).isEqualTo(WithdrawalStatus.PAID);
        assertThat(result.getPaidAt()).isNotNull();
        verify(notificationEmailService).sendWithdrawalStatusChangedEmail(request, "Pagado");

        assertThat(meterRegistry.get("luxtrox.withdrawals.paid").counter().count()).isEqualTo(100.00);
    }

    @Test
    void markPaid_stillRequested_throws() {
        WithdrawalRequest request = requestedWithdrawal(user, new BigDecimal("100.00"));
        // nunca se aprobo -- sigue REQUESTED
        when(withdrawalRequestRepository.findById(request.getId())).thenReturn(Optional.of(request));

        assertThrows(BusinessRuleException.class, () -> service.markPaid(request.getId(), admin));
    }

    @Test
    void markPaid_unknownId_throwsResourceNotFound() {
        UUID id = UUID.randomUUID();
        when(withdrawalRequestRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.markPaid(id, admin));
    }
}