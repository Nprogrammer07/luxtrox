package com.luxtrox.backend.controller;

import com.luxtrox.backend.dto.config.AdminConfigResponse;
import com.luxtrox.backend.dto.config.AdminConfigUpdateRequest;
import com.luxtrox.backend.service.SystemConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/admin/config")
@Tag(name = "Admin - Config", description = "Configuración mutable de la plataforma")
public class AdminConfigController {

    private final SystemConfigService configService;

    public AdminConfigController(SystemConfigService configService) {
        this.configService = configService;
    }

    @GetMapping
    @Operation(summary = "Obtener la configuración actual de la plataforma")
    public AdminConfigResponse getConfig() {
        return toResponse();
    }

    @PutMapping
    @Operation(summary = "Actualizar la configuración de la plataforma. "
            + "Los cambios afectan NUEVAS compras/retiros (las posiciones existentes no cambian).")
    public AdminConfigResponse updateConfig(@Valid @RequestBody AdminConfigUpdateRequest request) {
        configService.update(SystemConfigService.KEY_DRIVER_PRICE,
                request.driverPrice().toPlainString());
        configService.update(SystemConfigService.KEY_MAX_DRIVER_POSITIONS,
                String.valueOf(request.maxDriverPositions()));
        configService.update(SystemConfigService.KEY_CASHBACK_RATE_PCT,
                request.cashbackRatePct().toPlainString());
        configService.update(SystemConfigService.KEY_MIN_WITHDRAWAL,
                request.minWithdrawal().toPlainString());
        return toResponse();
    }

    private AdminConfigResponse toResponse() {
        // cashback se guarda como multiplicador (3.0) -- se convierte a
        // porcentaje legible (300) solo para la respuesta al frontend.
        BigDecimal cashbackPct = configService.getCashbackRate()
                .multiply(new BigDecimal("100")).stripTrailingZeros();
        return new AdminConfigResponse(
                configService.getDriverPrice(),
                configService.getMaxDriverPositions(),
                cashbackPct,
                configService.getMinWithdrawal()
        );
    }
}
