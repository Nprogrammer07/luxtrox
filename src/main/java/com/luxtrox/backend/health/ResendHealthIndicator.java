package com.luxtrox.backend.health;

import com.luxtrox.backend.integration.email.ResendProperties;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Aparece como "resend" dentro de /actuator/health. Ver
 * NowPaymentsHealthIndicator para el porque solo verifica presencia,
 * no validez real de la API key.
 */
@Component("resend")
public class ResendHealthIndicator implements HealthIndicator {

    private final ResendProperties properties;

    public ResendHealthIndicator(ResendProperties properties) {
        this.properties = properties;
    }

    @Override
    public Health health() {
        List<String> missing = new ArrayList<>();
        if (ConfigHealthCheck.isMissing(properties.getApiKey())) {
            missing.add("apiKey");
        }
        if (ConfigHealthCheck.isMissing(properties.getFromAddress())) {
            missing.add("fromAddress");
        }

        if (missing.isEmpty()) {
            return Health.up().build();
        }
        return Health.down()
                .withDetail("missingProperties", missing)
                .withDetail("reason", "Configuracion de Resend incompleta -- los correos transaccionales fallaran")
                .build();
    }
}