package com.luxtrox.backend.service;

import com.luxtrox.backend.dto.withdrawal.AdminWithdrawalResponse;
import com.luxtrox.backend.entity.*;
import com.luxtrox.backend.entity.enums.BankAccountType;
import com.luxtrox.backend.entity.enums.WithdrawalStatus;
import com.luxtrox.backend.entity.enums.WithdrawalType;
import com.luxtrox.backend.exception.BusinessRuleException;
import com.luxtrox.backend.exception.ResourceNotFoundException;
import com.luxtrox.backend.repository.BankWithdrawalDetailRepository;
import com.luxtrox.backend.repository.CryptoWithdrawalDetailRepository;
import com.luxtrox.backend.repository.UserRepository;
import com.luxtrox.backend.repository.WithdrawalRequestRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Implementa el algoritmo de retiros descrito en docs/domain-model.md
 * §4.3. El monto se descuenta de available_balance INMEDIATAMENTE al
 * solicitar; el usuario no puede cancelar -- solo el admin puede
 * rechazar (lo cual SI devuelve el saldo).
 */
@Service
public class WithdrawalService {

    private final WithdrawalRequestRepository withdrawalRequestRepository;
    private final CryptoWithdrawalDetailRepository cryptoDetailRepository;
    private final BankWithdrawalDetailRepository bankDetailRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final NotificationEmailService notificationEmailService;
    private final MeterRegistry meterRegistry;
    private final SystemConfigService systemConfigService;

    public WithdrawalService(WithdrawalRequestRepository withdrawalRequestRepository,
                              CryptoWithdrawalDetailRepository cryptoDetailRepository,
                              BankWithdrawalDetailRepository bankDetailRepository,
                              UserRepository userRepository,
                              AuditService auditService,
                              NotificationEmailService notificationEmailService,
                              MeterRegistry meterRegistry,
                              SystemConfigService systemConfigService) {
        this.withdrawalRequestRepository = withdrawalRequestRepository;
        this.cryptoDetailRepository = cryptoDetailRepository;
        this.bankDetailRepository = bankDetailRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.notificationEmailService = notificationEmailService;
        this.meterRegistry = meterRegistry;
        this.systemConfigService = systemConfigService;
    }

    @Transactional
    public WithdrawalRequest requestCrypto(User user, BigDecimal amount, String fullName, String email,
                                            String phone, String blockchainNetwork, String walletAddress) {
        WithdrawalRequest request = createBaseRequest(user, WithdrawalType.CRYPTO, amount);
        cryptoDetailRepository.save(new CryptoWithdrawalDetail(
                request, fullName, email, phone, blockchainNetwork, walletAddress));
        return request;
    }

    @Transactional
    public WithdrawalRequest requestBank(User user, BigDecimal amount, String fullName, String email, String phone,
                                          String country, String bankName, BankAccountType accountType,
                                          String accountNumber, String accountHolderName, String documentId) {
        WithdrawalRequest request = createBaseRequest(user, WithdrawalType.BANK, amount);
        bankDetailRepository.save(new BankWithdrawalDetail(
                request, fullName, email, phone, country, bankName, accountType,
                accountNumber, accountHolderName, documentId));
        return request;
    }

    private WithdrawalRequest createBaseRequest(User user, WithdrawalType type, BigDecimal amount) {
        BigDecimal minAmount = systemConfigService.getMinWithdrawal();
        if (amount == null || amount.compareTo(minAmount) < 0) {
            throw new BusinessRuleException("El monto minimo de retiro es $" + minAmount);
        }
        if (amount.compareTo(user.getAvailableBalance()) > 0) {
            throw new BusinessRuleException("El monto solicitado supera el saldo disponible");
        }

        BigDecimal oldBalance = user.getAvailableBalance();
        user.setAvailableBalance(user.getAvailableBalance().subtract(amount));
        userRepository.save(user);

        WithdrawalRequest request = withdrawalRequestRepository.save(new WithdrawalRequest(user, type, amount));

        auditService.record(user, "User", user.getId(), "WITHDRAWAL_REQUESTED_BALANCE_DEDUCTED",
                oldBalance, user.getAvailableBalance());
        auditService.record(user, "WithdrawalRequest", request.getId(), "REQUESTED",
                null, request.getStatus());

        Counter.builder("luxtrox.withdrawals.requested")
                .description("Retiros solicitados, por tipo")
                .tag("type", type.name())
                .register(meterRegistry)
                .increment();

        return request;
    }

    @Transactional
    public WithdrawalRequest approve(UUID withdrawalId, User admin) {
        WithdrawalRequest request = getRequestedOrThrow(withdrawalId);

        request.setStatus(WithdrawalStatus.APPROVED);
        request.setProcessedAt(OffsetDateTime.now());
        request.setProcessedByAdmin(admin);
        withdrawalRequestRepository.save(request);

        auditService.record(admin, "WithdrawalRequest", request.getId(), "APPROVED",
                WithdrawalStatus.REQUESTED, WithdrawalStatus.APPROVED);
        meterRegistry.counter("luxtrox.withdrawals.approved").increment();
        notifyStatusChangeQuietly(request, "Aprobado");
        return request;
    }

    /** Rechazar SI devuelve el saldo al usuario (ver docs/domain-model.md 4.3). */
    @Transactional
    public WithdrawalRequest reject(UUID withdrawalId, User admin, String adminNotes) {
        WithdrawalRequest request = getRequestedOrThrow(withdrawalId);

        request.setStatus(WithdrawalStatus.REJECTED);
        request.setProcessedAt(OffsetDateTime.now());
        request.setProcessedByAdmin(admin);
        request.setAdminNotes(adminNotes);
        withdrawalRequestRepository.save(request);

        User user = request.getUser();
        BigDecimal oldBalance = user.getAvailableBalance();
        user.setAvailableBalance(user.getAvailableBalance().add(request.getAmount()));
        userRepository.save(user);

        auditService.record(admin, "WithdrawalRequest", request.getId(), "REJECTED",
                WithdrawalStatus.REQUESTED, WithdrawalStatus.REJECTED);
        auditService.record(user, "User", user.getId(), "WITHDRAWAL_REJECTED_BALANCE_REFUNDED",
                oldBalance, user.getAvailableBalance());
        meterRegistry.counter("luxtrox.withdrawals.rejected").increment();
        notifyStatusChangeQuietly(request, "Rechazado");

        return request;
    }

    @Transactional
    public WithdrawalRequest markPaid(UUID withdrawalId, User admin) {
        WithdrawalRequest request = withdrawalRequestRepository.findById(withdrawalId)
                .orElseThrow(() -> new ResourceNotFoundException("Retiro no encontrado"));

        if (request.getStatus() != WithdrawalStatus.APPROVED) {
            throw new BusinessRuleException("Solo se puede marcar como pagado un retiro ya APPROVED");
        }

        request.setStatus(WithdrawalStatus.PAID);
        request.setPaidAt(OffsetDateTime.now());
        withdrawalRequestRepository.save(request);

        auditService.record(admin, "WithdrawalRequest", request.getId(), "PAID",
                WithdrawalStatus.APPROVED, WithdrawalStatus.PAID);

        Counter.builder("luxtrox.withdrawals.paid")
                .description("Total en dolares efectivamente pagado en retiros")
                .register(meterRegistry)
                .increment(request.getAmount().doubleValue());

        notifyStatusChangeQuietly(request, "Pagado");
        return request;
    }

    /**
     * Igual que en PurchaseService: un fallo de Resend nunca debe
     * tumbar la transaccion de un retiro real (aprobar/rechazar/pagar
     * ya movieron dinero de verdad antes de llegar aqui).
     */
    private void notifyStatusChangeQuietly(WithdrawalRequest request, String statusLabel) {
        try {
            notificationEmailService.sendWithdrawalStatusChangedEmail(request, statusLabel);
        } catch (Exception e) {
            auditService.recordSystemAction("WithdrawalRequest", request.getId(), "EMAIL_NOTIFICATION_FAILED",
                    null, e.getMessage());
        }
    }

    private WithdrawalRequest getRequestedOrThrow(UUID withdrawalId) {
        WithdrawalRequest request = withdrawalRequestRepository.findById(withdrawalId)
                .orElseThrow(() -> new ResourceNotFoundException("Retiro no encontrado"));
        if (request.getStatus() != WithdrawalStatus.REQUESTED) {
            throw new BusinessRuleException(
                    "Solo se puede procesar un retiro en estado REQUESTED (estado actual: "
                            + request.getStatus() + ")");
        }
        return request;
    }

    /**
     * Para AdminWithdrawalController -- listado de todas las
     * solicitudes (admin), opcionalmente filtrado por status.
     */
    @Transactional(readOnly = true)
    public List<AdminWithdrawalResponse> listAll(WithdrawalStatus status) {
        List<WithdrawalRequest> requests = status != null
                ? withdrawalRequestRepository.findByStatus(status)
                : withdrawalRequestRepository.findAll();
        return requests.stream().map(this::toAdminResponse).toList();
    }

    /**
     * userName/userEmail/phone vienen de la "foto" guardada en
     * Crypto/BankWithdrawalDetail al momento de la solicitud, NO del
     * perfil actual del usuario -- representa con que datos se debe
     * procesar EL PAGO, que pueden diferir si el usuario actualizo su
     * perfil despues de pedir el retiro.
     */
    private AdminWithdrawalResponse toAdminResponse(WithdrawalRequest request) {
        String userName;
        String userEmail;
        String phone;
        String walletAddress = null;
        String network = null;
        String transactionHash = null;

        if (request.getType() == WithdrawalType.CRYPTO) {
            CryptoWithdrawalDetail detail = cryptoDetailRepository.findByWithdrawalRequest(request)
                    .orElseThrow(() -> new IllegalStateException(
                            "Retiro CRYPTO sin CryptoWithdrawalDetail -- inconsistencia de datos: " + request.getId()));
            userName = detail.getFullName();
            userEmail = detail.getEmail();
            phone = detail.getPhone();
            walletAddress = detail.getWalletAddress();
            network = detail.getBlockchainNetwork();
            transactionHash = detail.getTransactionHash();
        } else {
            BankWithdrawalDetail detail = bankDetailRepository.findByWithdrawalRequest(request)
                    .orElseThrow(() -> new IllegalStateException(
                            "Retiro BANK sin BankWithdrawalDetail -- inconsistencia de datos: " + request.getId()));
            userName = detail.getFullName();
            userEmail = detail.getEmail();
            phone = detail.getPhone();
        }

        return new AdminWithdrawalResponse(
                request.getId(), request.getUser().getId(), userName, userEmail, phone,
                walletAddress, network, request.getAmount(), request.getStatus(),
                request.getAdminNotes(), request.getRequestedAt(), request.getProcessedAt(),
                request.getPaidAt(), transactionHash
        );
    }
}
