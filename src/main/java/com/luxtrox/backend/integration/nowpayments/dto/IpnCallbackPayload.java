package com.luxtrox.backend.integration.nowpayments.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

/**
 * Solo los campos del callback IPN que de verdad usamos -- NOWPayments
 * envia muchos mas, se ignoran a proposito (@JsonIgnoreProperties).
 *
 * IMPORTANTE: este DTO es solo para LEER los datos despues de que la
 * firma ya se valido sobre el body crudo (String). Nunca se usa este
 * objeto, ya parseado, para volver a calcular la firma -- eso debe
 * hacerse siempre sobre el JSON original, byte por byte.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record IpnCallbackPayload(
        @JsonProperty("payment_id") String paymentId,
        @JsonProperty("payment_status") String paymentStatus,
        @JsonProperty("order_id") String orderId,
        @JsonProperty("price_amount") BigDecimal priceAmount,
        @JsonProperty("price_currency") String priceCurrency,
        @JsonProperty("actually_paid") BigDecimal actuallyPaid
) {
    /**
     * "finished" es el estado FINAL de NOWPayments -- el dinero ya
     * esta efectivamente liquidado en nuestra wallet. "confirmed"
     * significa que la blockchain ya confirmo pero el paso de
     * conversion/liquidacion puede seguir pendiente -- a proposito NO
     * se acepta como suficiente para confirmar la compra.
     */
    public boolean isSuccessfullyPaid() {
        return "finished".equalsIgnoreCase(paymentStatus);
    }
}
