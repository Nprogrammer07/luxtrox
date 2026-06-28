package com.luxtrox.backend.controller;

import com.luxtrox.backend.dto.user.UpdateProfileRequest;
import com.luxtrox.backend.dto.user.UpdateUserStatusRequest;
import com.luxtrox.backend.dto.user.UserProfileResponse;
import com.luxtrox.backend.entity.enums.UserStatus;
import com.luxtrox.backend.security.CustomUserPrincipal;
import com.luxtrox.backend.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@Tag(name = "Users", description = "Perfil del usuario y gestion de usuarios (admin)")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/users/me")
    @Operation(summary = "Mi propio perfil")
    public UserProfileResponse me(@AuthenticationPrincipal CustomUserPrincipal principal) {
        return userService.getProfile(principal.getUser());
    }

    @PutMapping("/users/me")
    @Operation(summary = "Actualizar mi nombre y telefono")
    public UserProfileResponse updateMe(@AuthenticationPrincipal CustomUserPrincipal principal,
                                         @Valid @RequestBody UpdateProfileRequest request) {
        return userService.updateProfile(principal.getUser(), request);
    }

    @GetMapping("/admin/users")
    @Operation(summary = "Listar usuarios -- search y status son filtros opcionales")
    public List<UserProfileResponse> listUsers(@RequestParam(required = false) String search,
                                                @RequestParam(required = false) UserStatus status) {
        return userService.listUsers(search, status);
    }

    @GetMapping("/admin/users/{id}")
    @Operation(summary = "Ver el perfil de un usuario especifico")
    public UserProfileResponse getUser(@PathVariable UUID id) {
        return userService.getUserById(id);
    }

    @PatchMapping("/admin/users/{id}/status")
    @Operation(summary = "Cambiar el status de un usuario (ACTIVE/INACTIVE/SUSPENDED)")
    public UserProfileResponse updateStatus(@PathVariable UUID id,
                                             @Valid @RequestBody UpdateUserStatusRequest request) {
        return userService.updateUserStatus(id, request);
    }
}
