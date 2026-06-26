package com.luxtrox.backend.integration.nowpayments;

import com.luxtrox.backend.integration.nowpayments.dto.CreateInvoiceRequest;
import com.luxtrox.backend.integration.nowpayments.dto.CreateInvoiceResponse;
import com.luxtrox.backend.entity.Purchase;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;

/**
 * Crea el invoice de pago en NOWPayments para una compra CRYPTO. El
 * usuario es redirigido a invoiceUrl para pagar; NOWPayments notifica
 * el resultado via IPN (ver NowPaymentsWebhookController), no via
 * respuesta sincrona de esta llamada.
 */
@Component
public class NowPaymentsClient {

    private final RestClient restClient;
    private final NowPaymentsProperties properties;

    public NowPaymentsClient(NowPaymentsProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .defaultHeader("x-api-key", properties.getApiKey())
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    public CreateInvoiceResponse createInvoice(Purchase purchase) {
        CreateInvoiceRequest request = new CreateInvoiceRequest(
                roundToCents(purchase.getTotalAmount()),
                "usd",
                purchase.getId().toString(),
                describePurchase(purchase),
                properties.getIpnCallbackUrl(),
                properties.getSuccessUrl(),
                properties.getCancelUrl()
        );

        return restClient.post()
                .uri("/invoice")
                .body(request)
                .retrieve()
                .body(CreateInvoiceResponse.class);
    }

    private BigDecimal roundToCents(BigDecimal amount) {
        return amount.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private String describePurchase(Purchase purchase) {
        return "Luxtrox " + purchase.getPlanType().name() + " - compra " + purchase.getId();
    }
}
