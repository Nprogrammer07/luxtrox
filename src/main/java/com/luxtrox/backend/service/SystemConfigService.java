package com.luxtrox.backend.service;

import com.luxtrox.backend.entity.SystemConfig;
import com.luxtrox.backend.repository.SystemConfigRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Los 4 valores de plataforma configurables desde el panel de admin.
 * Cada getter tiene un fallback a la constante de PlanPricing para no
 * romper si la fila de la BD no existe aun (ej. en tests sin V23).
 *
 * cashback_rate_pct se guarda como porcentaje legible (300 = 300%) y
 * se devuelve como multiplicador decimal (3.0) para que PurchaseService
 * no cambie la logica de negocio.
 */
@Service
public class SystemConfigService {

    public static final String KEY_DRIVER_PRICE         = "driver_price";
    public static final String KEY_MAX_DRIVER_POSITIONS  = "max_driver_positions";
    public static final String KEY_CASHBACK_RATE_PCT     = "cashback_rate_pct";
    public static final String KEY_MIN_WITHDRAWAL        = "min_withdrawal";

    private final SystemConfigRepository repository;

    public SystemConfigService(SystemConfigRepository repository) {
        this.repository = repository;
    }

    public BigDecimal getDriverPrice() {
        return getBigDecimal(KEY_DRIVER_PRICE, PlanPricing.DRIVER_PACKAGE_PRICE);
    }

    public int getMaxDriverPositions() {
        return repository.findById(KEY_MAX_DRIVER_POSITIONS)
                .map(c -> Integer.parseInt(c.getValue()))
                .orElse(PlanPricing.MAX_DRIVER_PACKAGES);
    }

    /**
     * cashback_rate_pct se guarda como "300.00" (legible), se
     * devuelve como 3.0000 (multiplicador para PurchaseService).
     */
    public BigDecimal getCashbackRate() {
        BigDecimal pct = getBigDecimal(KEY_CASHBACK_RATE_PCT, PlanPricing.CASHBACK_MULTIPLIER.multiply(new BigDecimal("100")));
        return pct.divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP);
    }

    public BigDecimal getMinWithdrawal() {
        return getBigDecimal(KEY_MIN_WITHDRAWAL, new BigDecimal("50.00"));
    }

    @Transactional
    public void update(String key, String value) {
        SystemConfig config = repository.findById(key)
                .orElse(new SystemConfig(key, value, null));
        config.setValue(value);
        repository.save(config);
    }

    private BigDecimal getBigDecimal(String key, BigDecimal fallback) {
        return repository.findById(key)
                .map(c -> new BigDecimal(c.getValue()))
                .orElse(fallback);
    }
}
