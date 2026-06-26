package com.luxtrox.backend.integration.nowpayments.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Respuesta de POST /v1/invoice de NOWPayments (solo los campos que usamos). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CreateInvoiceResponse(
        @JsonProperty("id") String id,
        @JsonProperty("invoice_url") String invoiceUrl,
        @JsonProperty("order_id") String orderId
) {
}
