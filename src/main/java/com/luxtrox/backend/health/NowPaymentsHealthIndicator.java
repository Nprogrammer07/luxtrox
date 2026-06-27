package com.luxtrox.backend.health;

import com.luxtrox.backend.integration.nowpayments.NowPaymentsProperties;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Aparece como "nowPayments" dentro de /actuator/health (protegido
 * detras de rol ADMIN, ver SecurityConfig -- esto es informacion
 * operativa interna). Solo confirma PRESENCIA de las credenciales, no
 * que sean validas -- ver ConfigHealthCheck para el porque exacto.
 */
@Component("nowPayments")
public class NowPaymentsHealthIndicator implements HealthIndicator {

    private final NowPaymentsProperties properties;

    public NowPaymentsHealthIndicator(NowPaymentsProperties properties) {
        this.properties = properties;
    }

    @Override
    public Health health() {
        List<String> missing = new ArrayList<>();
        if (ConfigHealthCheck.isMissing(properties.getApiKey())) {
            missing.add("apiKey");
        }
        if (ConfigHealthCheck.isMissing(properties.getIpnSecret())) {
            missing.add("ipnSecret");
        }
        if (ConfigHealthCheck.isMissing(properties.getIpnCallbackUrl())) {
            missing.add("ipnCallbackUrl");
        }

        if (missing.isEmpty()) {
            return Health.up().build();
        }
        return Health.down()
                .withDetail("missingProperties", missing)
                .withDetail("reason", "Configuracion de NOWPayments incompleta -- los pagos cripto fallaran")
                .build();
    }
}
