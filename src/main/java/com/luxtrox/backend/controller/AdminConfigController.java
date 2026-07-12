package com.luxtrox.backend.controller;

import com.luxtrox.backend.dto.config.AdminConfigResponse;
import com.luxtrox.backend.dto.config.AdminConfigUpdateRequest;
import com.luxtrox.backend.service.SystemConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/config")
@Tag(name = "Admin - Config", description = "Configuración mutable de la plataforma")
public class AdminConfigController {

    private final SystemConfigService configService;

    public AdminConfigController(SystemConfigService configService) {
        this.configService = configService;
    }

    @GetMapping
    @Operation(summary = "Obtener la configuración actual")
    public AdminConfigResponse getConfig() {
        return new AdminConfigResponse(configService.getMinWithdrawal());
    }

    @PutMapping
    @Operation(summary = "Actualizar la configuración")
    public AdminConfigResponse updateConfig(@Valid @RequestBody AdminConfigUpdateRequest request) {
        configService.update(SystemConfigService.KEY_MIN_WITHDRAWAL,
                request.minWithdrawal().toPlainString());
        return new AdminConfigResponse(configService.getMinWithdrawal());
    }
}
