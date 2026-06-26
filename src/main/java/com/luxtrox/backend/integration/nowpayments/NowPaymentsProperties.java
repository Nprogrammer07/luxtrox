package com.luxtrox.backend.integration.nowpayments;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.nowpayments")
public class NowPaymentsProperties {

    private String apiKey;
    private String ipnSecret;
    private String baseUrl;
    private String ipnCallbackUrl;
    private String successUrl;
    private String cancelUrl;

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getIpnSecret() {
        return ipnSecret;
    }

    public void setIpnSecret(String ipnSecret) {
        this.ipnSecret = ipnSecret;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getIpnCallbackUrl() {
        return ipnCallbackUrl;
    }

    public void setIpnCallbackUrl(String ipnCallbackUrl) {
        this.ipnCallbackUrl = ipnCallbackUrl;
    }

    public String getSuccessUrl() {
        return successUrl;
    }

    public void setSuccessUrl(String successUrl) {
        this.successUrl = successUrl;
    }

    public String getCancelUrl() {
        return cancelUrl;
    }

    public void setCancelUrl(String cancelUrl) {
        this.cancelUrl = cancelUrl;
    }
}
