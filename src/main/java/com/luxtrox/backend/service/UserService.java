package com.luxtrox.backend.service;

import com.luxtrox.backend.dto.user.ManualCreditRequest;
import com.luxtrox.backend.dto.user.UpdateProfileRequest;
import com.luxtrox.backend.dto.user.UpdateUserStatusRequest;
import com.luxtrox.backend.dto.user.UserProfileResponse;
import com.luxtrox.backend.entity.CashbackTransaction;
import com.luxtrox.backend.entity.User;
import com.luxtrox.backend.entity.enums.CashbackTransactionType;
import com.luxtrox.backend.entity.enums.UserStatus;
import com.luxtrox.backend.exception.ResourceNotFoundException;
import com.luxtrox.backend.repository.CashbackTransactionRepository;
import com.luxtrox.backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Perfil del usuario + gestion de usuarios para admin.
 *
 * Tras eliminar el modulo Driver ya no existen posiciones: seminarsCount
 * y totalInvested se reportan como 0 / BigDecimal.ZERO (el tipo del
 * frontend los sigue esperando pero ya no aplican a Zenith/Plus).
 */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final CashbackTransactionRepository cashbackTransactionRepository;

    public UserService(UserRepository userRepository,
                       CashbackTransactionRepository cashbackTransactionRepository) {
        this.userRepository = userRepository;
        this.cashbackTransactionRepository = cashbackTransactionRepository;
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(User user) {
        return toResponse(freshCopyOf(user));
    }

    @Transactional
    public UserProfileResponse updateProfile(User user, UpdateProfileRequest request) {
        User freshUser = freshCopyOf(user);
        freshUser.setFullName(request.fullName());
        freshUser.setPhone(request.phone());
        userRepository.save(freshUser);
        return toResponse(freshUser);
    }

    @Transactional(readOnly = true)
    public List<UserProfileResponse> listUsers(String search, UserStatus status) {
        return userRepository.search(search, status).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getUserById(UUID userId) {
        return toResponse(findOrThrow(userId));
    }

    @Transactional
    public UserProfileResponse updateUserStatus(UUID userId, UpdateUserStatusRequest request) {
        User user = findOrThrow(userId);
        user.setStatus(request.status());
        userRepository.save(user);
        return toResponse(user);
    }

    /**
     * Credito manual del admin -- acredita amount a available_balance
     * del usuario y registra la transaccion como MANUAL_CREDIT para
     * auditoria completa. reason (PERFORMANCE/COMMISSION) queda en las
     * notes de la transaccion.
     */
    @Transactional
    public UserProfileResponse manualCredit(UUID userId, ManualCreditRequest request) {
        User user = findOrThrow(userId);
        user.setAvailableBalance(user.getAvailableBalance().add(request.amount()));
        userRepository.save(user);

        CashbackTransaction tx = new CashbackTransaction(user, CashbackTransactionType.MANUAL_CREDIT, request.amount());
        tx.setNotes("[" + request.reason().name() + "] " + request.notes());
        cashbackTransactionRepository.save(tx);

        return toResponse(user);
    }

    private User findOrThrow(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
    }

    private User freshCopyOf(User user) {
        return findOrThrow(user.getId());
    }

    private UserProfileResponse toResponse(User user) {
        // Sin modulo Driver ya no hay seminarios ni capital invertido.
        return UserProfileResponse.from(user, 0L, BigDecimal.ZERO);
    }
}
