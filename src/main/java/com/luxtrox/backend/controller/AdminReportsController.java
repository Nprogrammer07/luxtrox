package com.luxtrox.backend.controller;

import com.luxtrox.backend.dto.adminreports.AdminStatsResponse;
import com.luxtrox.backend.dto.adminreports.ChartDataPointResponse;
import com.luxtrox.backend.service.AdminReportsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Dashboard agregado de admin -- ver AdminReportsService para el
 * mapeo completo de cada campo contra las tablas reales.
 */
@RestController
@RequestMapping("/admin")
@Tag(name = "Admin - Reports", description = "Estadisticas agregadas y graficas para el dashboard de admin")
public class AdminReportsController {

    private final AdminReportsService adminReportsService;

    public AdminReportsController(AdminReportsService adminReportsService) {
        this.adminReportsService = adminReportsService;
    }

    @GetMapping("/stats")
    @Operation(summary = "Estadisticas agregadas del dashboard de admin")
    public ResponseEntity<AdminStatsResponse> stats() {
        return ResponseEntity.ok(adminReportsService.getStats());
    }

    @GetMapping("/reports/revenue")
    @Operation(summary = "Ingreso confirmado por mes, ultimos 6 meses")
    public ResponseEntity<List<ChartDataPointResponse>> revenueChart() {
        return ResponseEntity.ok(adminReportsService.getRevenueChart());
    }

    @GetMapping("/reports/users")
    @Operation(summary = "Usuarios nuevos por mes, ultimos 6 meses")
    public ResponseEntity<List<ChartDataPointResponse>> usersChart() {
        return ResponseEntity.ok(adminReportsService.getUsersGrowthChart());
    }

    @GetMapping("/reports/referrals")
    @Operation(summary = "Referidos nuevos por mes, ultimos 6 meses")
    public ResponseEntity<List<ChartDataPointResponse>> referralsChart() {
        return ResponseEntity.ok(adminReportsService.getReferralsChart());
    }
}