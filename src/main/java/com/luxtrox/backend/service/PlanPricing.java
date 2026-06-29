package com.luxtrox.backend.service;

import java.math.BigDecimal;

/**
 * Constantes de negocio centralizadas -- ver docs/domain-model.md
 * principios y adenda §7. Si el negocio vuelve a cambiar un precio o
 * porcentaje, este es el unico lugar que hay que tocar.
 */
public final class PlanPricing {

    private PlanPricing() {
    }

    public static final BigDecimal DRIVER_PACKAGE_PRICE = new BigDecimal("1099.00");
    public static final int MAX_DRIVER_PACKAGES = 30;
    public static final BigDecimal CASHBACK_MULTIPLIER = new BigDecimal("3.0");

    public static final BigDecimal ZENITH_PRICE = new BigDecimal("2299.00");
    public static final BigDecimal ZENITH_RENEWAL_PRICE = new BigDecimal("250.00");

    /** 9% -- comisión por referir una venta de Driver (ver §7.2). */
    public static final BigDecimal DRIVER_REFERRAL_RATE = new BigDecimal("0.09");

    /** 22% -- comisión por referir una venta de Zenith (antes 40%, ver adenda correspondiente en domain-model.md). */
    public static final BigDecimal ZENITH_REFERRAL_RATE = new BigDecimal("0.22");
}
