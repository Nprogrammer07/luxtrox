package com.luxtrox.backend.service;

import com.luxtrox.backend.dto.user.UpdateProfileRequest;
import com.luxtrox.backend.dto.user.UpdateUserStatusRequest;
import com.luxtrox.backend.dto.user.UserProfileResponse;
import com.luxtrox.backend.entity.User;
import com.luxtrox.backend.entity.enums.UserStatus;
import com.luxtrox.backend.exception.ResourceNotFoundException;
import com.luxtrox.backend.repository.InvestmentPositionRepository;
import com.luxtrox.backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Perfil del usuario + gestion de usuarios para admin -- pedido por
 * el frontend (Next.js), que ya tenia un UserController completo
 * imaginado (GET/PUT /users/me, GET/PATCH /admin/users/...) antes de
 * que este backend existiera. No existia ningun controller para esto
 * hasta ahora.
 *
 * Simplificaciones deliberadas (ver tambien UserProfileResponse):
 * - avatar/country/walletAddress/blockchainNetwork del tipo `User`
 *   del frontend: no tienen columna real -- se omiten de la
 *   respuesta (son opcionales en ese tipo).
 * - GET /admin/users/{id} devuelve el mismo UserProfileResponse que
 *   /users/me, SIN el `AdminUser` extendido que el frontend tambien
 *   define (seminars/cashbackSummary/withdrawals anidados) -- esa
 *   agregacion mas pesada queda pendiente; si se necesita, el
 *   frontend puede llamar /cashback/summary y /withdrawals por
 *   separado mientras tanto.
 * - listUsers() no implementa paginacion real de servidor (igual que
 *   los demas listados de admin de este backend) ni evita el N+1 al
 *   calcular seminarsCount/totalInvested por cada usuario listado --
 *   aceptable para una base de usuarios de etapa temprana, revisar si
 *   eso cambia.
 */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final InvestmentPositionRepository positionRepository;

    public UserService(UserRepository userRepository, InvestmentPositionRepository positionRepository) {
        this.userRepository = userRepository;
        this.positionRepository = positionRepository;
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

    private User findOrThrow(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
    }

    /**
     * El User que llega como parametro a getProfile()/updateProfile()
     * (via CustomUserPrincipal.getUser(), cargado durante la
     * autenticacion del request, ANTES de que arrancara la
     * transaccion de este metodo) viene de una sesion de Hibernate ya
     * cerrada -- abrir un @Transactional nuevo aqui NO reconecta
     * automaticamente ese objeto a la sesion actual. Volver a
     * cargarlo por su id si garantiza que sus relaciones LAZY
     * (referredBy) queden atadas a la sesion abierta de ESTE metodo.
     */
    private User freshCopyOf(User user) {
        return findOrThrow(user.getId());
    }

    private UserProfileResponse toResponse(User user) {
        long seminarsCount = positionRepository.countByUser(user);
        BigDecimal totalInvested = positionRepository.sumCapitalByUser(user);
        return UserProfileResponse.from(user, seminarsCount, totalInvested);
    }
}
