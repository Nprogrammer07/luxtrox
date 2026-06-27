package com.luxtrox.backend.health;

import com.luxtrox.backend.integration.storage.StorageProperties;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Aparece como "storage" dentro de /actuator/health. Ver
 * NowPaymentsHealthIndicator para el porque solo verifica presencia,
 * no validez real de las credenciales.
 */
@Component("storage")
public class StorageHealthIndicator implements HealthIndicator {

    private final StorageProperties properties;

    public StorageHealthIndicator(StorageProperties properties) {
        this.properties = properties;
    }

    @Override
    public Health health() {
        List<String> missing = new ArrayList<>();
        if (ConfigHealthCheck.isMissing(properties.getEndpoint())) {
            missing.add("endpoint");
        }
        if (ConfigHealthCheck.isMissing(properties.getRegion())) {
            missing.add("region");
        }
        if (ConfigHealthCheck.isMissing(properties.getAccessKeyId())) {
            missing.add("accessKeyId");
        }
        if (ConfigHealthCheck.isMissing(properties.getSecretAccessKey())) {
            missing.add("secretAccessKey");
        }
        if (ConfigHealthCheck.isMissing(properties.getBucket())) {
            missing.add("bucket");
        }

        if (missing.isEmpty()) {
            return Health.up().build();
        }
        return Health.down()
                .withDetail("missingProperties", missing)
                .withDetail("reason", "Configuracion de Storage incompleta -- las facturas PDF no se podran subir")
                .build();
    }
}
