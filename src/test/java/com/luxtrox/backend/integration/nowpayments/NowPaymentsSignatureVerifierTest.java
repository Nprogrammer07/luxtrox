package com.luxtrox.backend.integration.nowpayments;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test unitario puro -- sin Spring, sin red, sin base de datos.
 *
 * El vector de prueba (payload, secreto y firma esperada) se generó y
 * verificó de forma INDEPENDIENTE con Python (hmac + hashlib, sin
 * relación con este código), replicando exactamente el mecanismo
 * documentado por NOWPayments: orden recursivo de claves + JSON
 * compacto + HMAC-SHA512. Si este test pasa, confirma que la
 * implementación en Java hace lo mismo que esa referencia independiente.
 */
class NowPaymentsSignatureVerifierTest {

    private final NowPaymentsSignatureVerifier verifier = new NowPaymentsSignatureVerifier();

    private static final String IPN_SECRET = "test-ipn-secret-12345";

    // 3 niveles de anidamiento, claves fuera de orden a proposito en
    // cada nivel -- prueba que el ordenamiento es REALMENTE recursivo,
    // no solo en el primer nivel.
    private static final String PAYLOAD = "{\"payment_status\": \"finished\", \"payment_id\": \"5077125051\", "
            + "\"order_id\": \"test-order-123\", \"outcome\": {\"currency\": \"usd\", \"amount\": \"1099\", "
            + "\"nested\": {\"zeta\": \"last-alphabetically\", \"alpha\": \"first-alphabetically\"}}}";

    private static final String EXPECTED_SIGNATURE =
            "1317dae6f3e9dd459d3b8f6d116beb4676c03bcb95e41df8a3cc39e964436b91d3917b314b7c5844b487c99cbf7331b46f480948e69d7462ddb0e7172b9378fe";

    @Test
    void computesTheExactSignatureFromTheIndependentlyVerifiedVector() throws Exception {
        String computed = verifier.computeSignature(PAYLOAD, IPN_SECRET);
        assertThat(computed).isEqualToIgnoringCase(EXPECTED_SIGNATURE);
    }

    @Test
    void acceptsAValidSignature() {
        assertThat(verifier.isValid(PAYLOAD, EXPECTED_SIGNATURE, IPN_SECRET)).isTrue();
    }

    @Test
    void rejectsATamperedPayload() {
        String tampered = PAYLOAD.replace("\"5077125051\"", "\"9999999999\"");
        assertThat(verifier.isValid(tampered, EXPECTED_SIGNATURE, IPN_SECRET)).isFalse();
    }

    @Test
    void rejectsTheCorrectPayloadSignedWithTheWrongSecret() {
        assertThat(verifier.isValid(PAYLOAD, EXPECTED_SIGNATURE, "wrong-secret")).isFalse();
    }

    @Test
    void rejectsMalformedJsonInsteadOfThrowing() {
        assertThat(verifier.isValid("esto no es json", EXPECTED_SIGNATURE, IPN_SECRET)).isFalse();
    }

    @Test
    void rejectsANullOrBlankSignatureHeader() {
        assertThat(verifier.isValid(PAYLOAD, null, IPN_SECRET)).isFalse();
        assertThat(verifier.isValid(PAYLOAD, "", IPN_SECRET)).isFalse();
        assertThat(verifier.isValid(PAYLOAD, "   ", IPN_SECRET)).isFalse();
    }

    @Test
    void keyOrderInTheOriginalPayloadDoesNotMatter_onlyTheFinalSortedFormDoes() {
        // Mismo contenido, pero escrito con las claves YA en otro
        // orden distinto al original -- el resultado de la firma debe
        // ser identico, porque el algoritmo siempre re-ordena antes de
        // firmar.
        String reordered = "{\"order_id\": \"test-order-123\", \"payment_id\": \"5077125051\", "
                + "\"payment_status\": \"finished\", \"outcome\": {\"nested\": {\"alpha\": \"first-alphabetically\", "
                + "\"zeta\": \"last-alphabetically\"}, \"amount\": \"1099\", \"currency\": \"usd\"}}";

        assertThat(verifier.isValid(reordered, EXPECTED_SIGNATURE, IPN_SECRET)).isTrue();
    }
}
