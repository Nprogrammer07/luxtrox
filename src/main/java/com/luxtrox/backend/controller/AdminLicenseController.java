package com.luxtrox.backend.controller;

import com.luxtrox.backend.dto.license.AdminLicenseResponse;
import com.luxtrox.backend.entity.PlusLicense;
import com.luxtrox.backend.entity.User;
import com.luxtrox.backend.entity.ZenithLicense;
import com.luxtrox.backend.repository.PlusLicenseRepository;
import com.luxtrox.backend.repository.ZenithLicenseRepository;
import com.luxtrox.backend.service.PlanPricing;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Listado de TODAS las licencias (Zenith y Genius) de todos los usuarios,
 * para que el admin gestione las renovaciones anuales.
 *
 * @Transactional porque license.getUser() es LAZY y se lee en el mapeo.
 * Se ordena por fecha de vencimiento ascendente: lo que vence primero
 * (o ya venció) aparece arriba, que es lo que el admin necesita atender.
 */
@RestController
@RequestMapping("/admin/licenses")
@Tag(name = "Admin - Licenses", description = "Licencias Zenith y Genius de todos los usuarios")
public class AdminLicenseController {

    private final ZenithLicenseRepository zenithRepository;
    private final PlusLicenseRepository plusRepository;

    public AdminLicenseController(ZenithLicenseRepository zenithRepository,
                                   PlusLicenseRepository plusRepository) {
        this.zenithRepository = zenithRepository;
        this.plusRepository = plusRepository;
    }

    @GetMapping
    @Transactional(readOnly = true)
    @Operation(summary = "Listar todas las licencias Zenith y Genius, ordenadas por vencimiento")
    public List<AdminLicenseResponse> listAll() {
        List<AdminLicenseResponse> all = new ArrayList<>();

        // Genius es pago único (acceso indefinido) -> no se renueva, así que
        // no aparece en el panel de renovaciones. Solo Zenith es renovable.
        // El mapeo fromPlus() se conserva latente por si Genius vuelve a ser
        // renovable en el futuro.
        for (ZenithLicense license : zenithRepository.findAll()) {
            all.add(fromZenith(license));
        }

        all.sort(Comparator.comparing(AdminLicenseResponse::currentPeriodEnd));
        return all;
    }

    private AdminLicenseResponse fromZenith(ZenithLicense license) {
        User user = license.getUser();
        return new AdminLicenseResponse(
                license.getId(),
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                "ZENITH",
                license.getStatus().name().toLowerCase(),
                license.getActivatedAt(),
                license.getCurrentPeriodEnd(),
                daysUntil(license.getCurrentPeriodEnd()),
                PlanPricing.ZENITH_RENEWAL_PRICE
        );
    }

    @SuppressWarnings("unused")  // latente: Genius es pago único, hoy no se lista aquí
    private AdminLicenseResponse fromPlus(PlusLicense license) {
        User user = license.getUser();
        return new AdminLicenseResponse(
                license.getId(),
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                "PLUS",
                license.getStatus().name().toLowerCase(),
                license.getPurchasedAt(),
                license.getCurrentPeriodEnd(),
                daysUntil(license.getCurrentPeriodEnd()),
                PlanPricing.PLUS_RENEWAL_PRICE
        );
    }

    /** Negativo si ya venció. */
    private long daysUntil(OffsetDateTime periodEnd) {
        return ChronoUnit.DAYS.between(OffsetDateTime.now(), periodEnd);
    }
}