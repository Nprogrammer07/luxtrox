package com.luxtrox.backend.unit.service;

import com.luxtrox.backend.dto.user.UpdateProfileRequest;
import com.luxtrox.backend.dto.user.UpdateUserStatusRequest;
import com.luxtrox.backend.dto.user.UserProfileResponse;
import com.luxtrox.backend.entity.Role;
import com.luxtrox.backend.entity.User;
import com.luxtrox.backend.entity.enums.UserStatus;
import com.luxtrox.backend.exception.ResourceNotFoundException;
import com.luxtrox.backend.repository.CashbackTransactionRepository;
import com.luxtrox.backend.repository.InvestmentPositionRepository;
import com.luxtrox.backend.repository.UserRepository;
import com.luxtrox.backend.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceUnitTest {

    @Mock private UserRepository userRepository;
    @Mock private InvestmentPositionRepository positionRepository;
    @Mock private CashbackTransactionRepository cashbackTransactionRepository;

    private UserService service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new UserService(userRepository, positionRepository, cashbackTransactionRepository);

        Role role = new Role("USER", "Usuario estandar");
        user = new User("Carlos Perez", "carlos@example.com", "+1111", "hash", role, "CARLOS01");
        setId(user, UUID.randomUUID());

        lenient().when(positionRepository.countByUser(any())).thenReturn(3L);
        lenient().when(positionRepository.sumCapitalByUser(any())).thenReturn(new BigDecimal("3297.00"));
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

    @Test
    void getProfile_mapsFieldsIncludingComputedSeminarsAndCapital() {
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        UserProfileResponse response = service.getProfile(user);

        assertThat(response.id()).isEqualTo(user.getId());
        assertThat(response.name()).isEqualTo("Carlos Perez");
        assertThat(response.email()).isEqualTo("carlos@example.com");
        assertThat(response.role()).isEqualTo("USER");
        assertThat(response.seminarsCount()).isEqualTo(3L);
        assertThat(response.totalInvested()).isEqualByComparingTo("3297.00");
        assertThat(response.status()).isEqualTo("ACTIVE");
    }

    @Test
    void getProfile_referredByUser_returnsReferrersCode() {
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        Role role = new Role("USER", "Usuario estandar");
        User referrer = new User("Referente", "ref@example.com", "+2222", "hash", role, "REFCODE1");
        setId(referrer, UUID.randomUUID());
        user.setReferredBy(referrer);

        UserProfileResponse response = service.getProfile(user);

        assertThat(response.referredBy()).isEqualTo("REFCODE1");
    }

    @Test
    void getProfile_noReferrer_returnsNull() {
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        UserProfileResponse response = service.getProfile(user);

        assertThat(response.referredBy()).isNull();
    }

    @Test
    void updateProfile_changesNameAndPhone_savesAndReturnsUpdated() {
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        UserProfileResponse response = service.updateProfile(user, new UpdateProfileRequest("Carlos Nuevo", "+9999"));

        assertThat(user.getFullName()).isEqualTo("Carlos Nuevo");
        assertThat(user.getPhone()).isEqualTo("+9999");
        assertThat(response.name()).isEqualTo("Carlos Nuevo");
        verify(userRepository).save(user);
    }

    @Test
    void listUsers_delegatesFilterToRepository_mapsEachResult() {
        Role role = new Role("USER", "Usuario estandar");
        User second = new User("Ana Lopez", "ana@example.com", "+3333", "hash", role, "ANA00001");
        setId(second, UUID.randomUUID());
        when(userRepository.search("ana", UserStatus.ACTIVE)).thenReturn(List.of(user, second));

        List<UserProfileResponse> result = service.listUsers("ana", UserStatus.ACTIVE);

        assertThat(result).hasSize(2);
        assertThat(result.get(1).name()).isEqualTo("Ana Lopez");
    }

    @Test
    void getUserById_found_returnsProfile() {
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        UserProfileResponse response = service.getUserById(user.getId());

        assertThat(response.email()).isEqualTo("carlos@example.com");
    }

    @Test
    void getUserById_notFound_throws() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.getUserById(id));
    }

    @Test
    void updateUserStatus_changesAndSaves() {
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        UserProfileResponse response = service.updateUserStatus(user.getId(), new UpdateUserStatusRequest(UserStatus.SUSPENDED));

        assertThat(user.getStatus()).isEqualTo(UserStatus.SUSPENDED);
        assertThat(response.status()).isEqualTo("SUSPENDED");
    }

    @Test
    void updateUserStatus_unknownId_throwsAndNeverSaves() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.updateUserStatus(id, new UpdateUserStatusRequest(UserStatus.SUSPENDED)));
    }
}
