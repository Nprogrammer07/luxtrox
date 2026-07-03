package com.luxtrox.backend.service;

import java.math.BigDecimal;

/**
 * Constantes de precio y tasas de los planes de la plataforma.
 * Los valores de DRIVER, MAX_DRIVER y CASHBACK_MULTIPLIER son fallback —
 * los valores reales en producción vienen de SystemConfigService (BD).
 */
public class PlanPricing {

    // Plan Driver
    public static final BigDecimal DRIVER_PACKAGE_PRICE  = new BigDecimal("1099.00");
    public static final int        MAX_DRIVER_PACKAGES   = 30;
    public static final BigDecimal CASHBACK_MULTIPLIER   = new BigDecimal("3.00");
    public static final BigDecimal DRIVER_REFERRAL_RATE  = new BigDecimal("0.09");

    // Plan Zenith
    public static final BigDecimal ZENITH_PRICE          = new BigDecimal("2299.00");
    public static final BigDecimal ZENITH_RENEWAL_PRICE  = new BigDecimal("250.00");
    public static final BigDecimal ZENITH_REFERRAL_RATE  = new BigDecimal("0.22");

    // Plan Plus
    public static final BigDecimal PLUS_PRICE            = new BigDecimal("200.00");
    public static final BigDecimal PLUS_REFERRAL_RATE    = new BigDecimal("0.25");  // 25% = $50
    public static final BigDecimal PLUS_ZENITH_DISCOUNT  = new BigDecimal("100.00"); // desc. al comprar Zenith

    private PlanPricing() {}
}
