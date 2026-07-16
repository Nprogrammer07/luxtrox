package com.luxtrox.backend.service;

import java.math.BigDecimal;

public class PlanPricing {

    // Plan Zenith -- bot de trading, $2,299 + renovación anual $250
    public static final BigDecimal ZENITH_PRICE          = new BigDecimal("2299.00");
    public static final BigDecimal ZENITH_RENEWAL_PRICE  = new BigDecimal("250.00");
    public static final BigDecimal ZENITH_REFERRAL_RATE  = new BigDecimal("0.22");

    // Plan Genius (interno: PLUS) -- pago ÚNICO de $89, acceso indefinido.
    public static final BigDecimal PLUS_PRICE            = new BigDecimal("89.00");
    /**
     * Comisión de referido de Genius: monto FIJO de $19 (no un porcentaje).
     * A diferencia de Zenith (que usa una tasa sobre el precio), Genius
     * paga siempre $19 al referente sin importar nada más.
     */
    public static final BigDecimal PLUS_REFERRAL_FLAT    = new BigDecimal("19.00");
    public static final BigDecimal PLUS_ZENITH_DISCOUNT  = new BigDecimal("100.00");

    // --- Latente (renovación de Genius, hoy sin uso) ---
    // Genius es pago único; estos valores quedan por si se reactiva el
    // modelo de renovación anual en el futuro.
    public static final BigDecimal PLUS_RENEWAL_PRICE    = new BigDecimal("89.00");
    public static final BigDecimal PLUS_REFERRAL_RATE    = new BigDecimal("0.25");

    private PlanPricing() {}
}
