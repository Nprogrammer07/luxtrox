package com.luxtrox.backend.service;

import java.math.BigDecimal;

public class PlanPricing {

    // Plan Zenith -- bot de trading, $2,299 + renovación anual $250
    public static final BigDecimal ZENITH_PRICE          = new BigDecimal("2299.00");
    public static final BigDecimal ZENITH_RENEWAL_PRICE  = new BigDecimal("250.00");
    public static final BigDecimal ZENITH_REFERRAL_RATE  = new BigDecimal("0.22");

    // Plan Genius (interno: PLUS) -- matrícula académica ANUAL de $200
    public static final BigDecimal PLUS_PRICE            = new BigDecimal("200.00");
    /** La renovación cuesta lo mismo que la matrícula inicial. */
    public static final BigDecimal PLUS_RENEWAL_PRICE    = new BigDecimal("200.00");
    public static final BigDecimal PLUS_REFERRAL_RATE    = new BigDecimal("0.25");  // 25% = $50
    public static final BigDecimal PLUS_ZENITH_DISCOUNT  = new BigDecimal("100.00");

    private PlanPricing() {}
}
