package com.luxtrox.backend.service;

import java.math.BigDecimal;

public class PlanPricing {

    // Plan Zenith
    public static final BigDecimal ZENITH_PRICE          = new BigDecimal("2299.00");
    public static final BigDecimal ZENITH_RENEWAL_PRICE  = new BigDecimal("250.00");
    public static final BigDecimal ZENITH_REFERRAL_RATE  = new BigDecimal("0.22");

    // Plan Plus
    public static final BigDecimal PLUS_PRICE            = new BigDecimal("200.00");
    public static final BigDecimal PLUS_REFERRAL_RATE    = new BigDecimal("0.25");
    public static final BigDecimal PLUS_ZENITH_DISCOUNT  = new BigDecimal("100.00");

    private PlanPricing() {}
}
