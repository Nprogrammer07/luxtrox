package com.luxtrox.backend.integration.nowpayments;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.TreeMap;

/**
 * Verifica la firma de los callbacks IPN de NOWPayments.
 *
 * Mecanismo exacto (confirmado contra la documentacion oficial de
 * NOWPayments, no asumido):
 *   1. El cuerpo JSON del callback se ordena RECURSIVAMENTE por clave
 *      (no solo el nivel superior -- cualquier objeto anidado tambien
 *      se ordena).
 *   2. Ese JSON ordenado se serializa de forma compacta (sin espacios,
 *      sin escapar "/").
 *   3. Se firma con HMAC-SHA512 usando el IPN secret como clave.
 *   4. El resultado en hexadecimal debe ser IDENTICO al header
 *      "x-nowpayments-sig" del request.
 *
 * Si alguno de estos 4 pasos esta mal, la firma nunca calza -- por eso
 * esta clase tiene su propio set de tests unitarios con vectores
 * conocidos, sin red ni base de datos de por medio.
 */
@Component
public class NowPaymentsSignatureVerifier {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public boolean isValid(String rawJsonBody, String receivedSignatureHeader, String ipnSecret) {
        if (rawJsonBody == null || receivedSignatureHeader == null || receivedSignatureHeader.isBlank()) {
            return false;
        }
        try {
            String computed = computeSignature(rawJsonBody, ipnSecret);
            return constantTimeEquals(computed, receivedSignatureHeader.trim().toLowerCase());
        } catch (Exception e) {
            // Cualquier fallo al parsear/firmar se trata como firma
            // invalida -- nunca se asume valido por defecto.
            return false;
        }
    }

    public String computeSignature(String rawJsonBody, String ipnSecret)
            throws NoSuchAlgorithmException, InvalidKeyException {
        try {
            JsonNode tree = objectMapper.readTree(rawJsonBody);
            JsonNode sorted = sortRecursively(tree);
            String compactSorted = objectMapper.writeValueAsString(sorted);

            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(ipnSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            byte[] digest = mac.doFinal(compactSorted.getBytes(StandardCharsets.UTF_8));

            return HexFormat.of().formatHex(digest);
        } catch (IOException e) {
            throw new IllegalArgumentException("Cuerpo del callback no es JSON valido", e);
        }
    }

    /**
     * Recorre el arbol JSON y devuelve uno equivalente donde TODO
     * objeto (a cualquier nivel de anidamiento) tiene sus claves en
     * orden alfabetico. Los arrays mantienen su orden original, pero
     * cualquier objeto DENTRO de un array tambien se ordena.
     */
    private JsonNode sortRecursively(JsonNode node) {
        if (node.isObject()) {
            TreeMap<String, JsonNode> sortedFields = new TreeMap<>();
            node.fields().forEachRemaining(entry -> sortedFields.put(entry.getKey(), sortRecursively(entry.getValue())));

            ObjectNode result = objectMapper.createObjectNode();
            sortedFields.forEach(result::set);
            return result;
        }
        if (node.isArray()) {
            ArrayNode result = objectMapper.createArrayNode();
            List<JsonNode> sortedElements = new ArrayList<>();
            node.forEach(element -> sortedElements.add(sortRecursively(element)));
            sortedElements.forEach(result::add);
            return result;
        }
        return node;
    }

    private boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8)
        );
    }
}
