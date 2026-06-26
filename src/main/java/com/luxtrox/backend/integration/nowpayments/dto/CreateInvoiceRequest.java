package com.luxtrox.backend.integration.nowpayments.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

/** Body para POST /v1/invoice de NOWPayments. */
public record CreateInvoiceRequest(
        @JsonProperty("price_amount") BigDecimal priceAmount,
        @JsonProperty("price_currency") String priceCurrency,
        @JsonProperty("order_id") String orderId,
        @JsonProperty("order_description") String orderDescription,
        @JsonProperty("ipn_callback_url") String ipnCallbackUrl,
        @JsonProperty("success_url") String successUrl,
        @JsonProperty("cancel_url") String cancelUrl
) {
}
