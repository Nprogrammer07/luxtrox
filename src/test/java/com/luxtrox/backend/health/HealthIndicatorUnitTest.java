package com.luxtrox.backend.health;

import com.luxtrox.backend.integration.email.ResendProperties;
import com.luxtrox.backend.integration.nowpayments.NowPaymentsProperties;
import com.luxtrox.backend.integration.storage.StorageProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Las tres clases solo verifican PRESENCIA de configuracion, no
 * validez real contra el proveedor externo -- ver el comentario de
 * ConfigHealthCheck para el porque exacto (variables totalmente
 * ausentes ya hacen fallar el arranque; esto cubre el caso que SI
 * pasa desapercibido: una variable vacia, o un placeholder sin
 * resolver filtrado tal cual).
 */
class HealthIndicatorsUnitTest {

    @Test
    void nowPayments_allPropertiesPresent_isUp() {
        NowPaymentsProperties props = new NowPaymentsProperties();
        props.setApiKey("real-api-key");
        props.setIpnSecret("real-ipn-secret");
        props.setIpnCallbackUrl("https://example.com/webhooks/nowpayments/ipn");

        Health health = new NowPaymentsHealthIndicator(props).health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void nowPayments_blankApiKey_isDownAndNamesTheMissingField() {
        NowPaymentsProperties props = new NowPaymentsProperties();
        props.setApiKey("   "); // en blanco, no null -- el caso que SI pasa desapercibido
        props.setIpnSecret("real-ipn-secret");
        props.setIpnCallbackUrl("https://example.com/webhooks/nowpayments/ipn");

        Health health = new NowPaymentsHealthIndicator(props).health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        @SuppressWarnings("unchecked")
        var missing = (java.util.List<String>) health.getDetails().get("missingProperties");
        assertThat(missing).containsExactly("apiKey");
    }

    @Test
    void nowPayments_unresolvedPlaceholderLeakedThrough_isDown() {
        NowPaymentsProperties props = new NowPaymentsProperties();
        props.setApiKey("${NOWPAYMENTS_API_KEY}"); // nunca se resolvio, quedo literal
        props.setIpnSecret("real-ipn-secret");
        props.setIpnCallbackUrl("https://example.com/webhooks/nowpayments/ipn");

        Health health = new NowPaymentsHealthIndicator(props).health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    }

    @Test
    void resend_allPropertiesPresent_isUp() {
        ResendProperties props = new ResendProperties();
        props.setApiKey("real-key");
        props.setFromAddress("no-reply@luxtrox.com");

        assertThat(new ResendHealthIndicator(props).health().getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void resend_nullFromAddress_isDown() {
        ResendProperties props = new ResendProperties();
        props.setApiKey("real-key");
        // fromAddress nunca se seteo -- queda null

        Health health = new ResendHealthIndicator(props).health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        @SuppressWarnings("unchecked")
        var missing = (java.util.List<String>) health.getDetails().get("missingProperties");
        assertThat(missing).containsExactly("fromAddress");
    }

    @Test
    void storage_allPropertiesPresent_isUp() {
        StorageProperties props = new StorageProperties();
        props.setEndpoint("https://example.supabase.co/storage/v1/s3");
        props.setRegion("us-east-1");
        props.setAccessKeyId("real-access-key");
        props.setSecretAccessKey("real-secret-key");
        props.setBucket("luxtrox-files");

        assertThat(new StorageHealthIndicator(props).health().getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void storage_multipleFieldsMissing_isDownAndListsAllOfThem() {
        StorageProperties props = new StorageProperties();
        props.setEndpoint("https://example.supabase.co/storage/v1/s3");
        props.setRegion("us-east-1");
        props.setBucket("luxtrox-files");
        // accessKeyId y secretAccessKey nunca se setearon

        Health health = new StorageHealthIndicator(props).health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        @SuppressWarnings("unchecked")
        var missing = (java.util.List<String>) health.getDetails().get("missingProperties");
        assertThat(missing).containsExactly("accessKeyId", "secretAccessKey");
    }
}