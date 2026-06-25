package com.luxtrox.backend.controller;

import com.luxtrox.backend.dto.cashback.MonthlyPerformanceResponse;
import com.luxtrox.backend.dto.cashback.RegisterPerformanceRequest;
import com.luxtrox.backend.entity.MonthlyPerformance;
import com.luxtrox.backend.security.CustomUserPrincipal;
import com.luxtrox.backend.service.CashbackDistributionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Registrar el % mensual y aplicarlo son dos pasos separados a
 * proposito (ver docs/domain-model.md 4.1) -- permite al admin
 * verificar el numero antes de disparar el algoritmo que mueve dinero
 * real entre miles de posiciones.
 */
@RestController
@RequestMapping("/admin/cashback")
@Tag(name = "Admin - Cashback", description = "Registro y distribucion del rendimiento mensual")
public class AdminCashbackController {

    private final CashbackDistributionService distributionService;

    public AdminCashbackController(CashbackDistributionService distributionService) {
        this.distributionService = distributionService;
    }

    @PostMapping("/monthly-performance")
    @Operation(summary = "Registrar el % de rendimiento de un mes (no lo aplica todavia)")
    public ResponseEntity<MonthlyPerformanceResponse> register(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @Valid @RequestBody RegisterPerformanceRequest request) {

        MonthlyPerformance performance = distributionService.registerPerformance(
                request.month(), request.year(), request.percentage(), principal.getUser());

        return ResponseEntity.ok(toResponse(performance));
    }

    @PostMapping("/monthly-performance/{performanceId}/distribute")
    @Operation(summary = "Aplicar un rendimiento ya registrado a todas las posiciones activas (idempotente)")
    public ResponseEntity<Void> distribute(@PathVariable UUID performanceId) {
        distributionService.distribute(performanceId);
        return ResponseEntity.noContent().build();
    }

    private MonthlyPerformanceResponse toResponse(MonthlyPerformance performance) {
        return new MonthlyPerformanceResponse(
                performance.getId(),
                performance.getMonth(),
                performance.getYear(),
                performance.getPercentage(),
                performance.getAppliedAt(),
                performance.getCreatedAt()
        );
    }
}
