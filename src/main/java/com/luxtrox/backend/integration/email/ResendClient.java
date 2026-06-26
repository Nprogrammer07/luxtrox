package com.luxtrox.backend.integration.email;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Base64;
import java.util.List;

/**
 * Cliente minimo de Resend (https://resend.com/docs/api-reference/emails/send-email).
 * Un solo endpoint, no hace falta su SDK de Java -- RestClient (ya
 * incluido en Spring Web) es suficiente.
 */
@Component
public class ResendClient {

    private final RestClient restClient;
    private final ResendProperties properties;

    public ResendClient(ResendProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.builder()
                .baseUrl("https://api.resend.com")
                .defaultHeader("Authorization", "Bearer " + properties.getApiKey())
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    public void sendHtml(String to, String subject, String htmlBody) {
        send(to, subject, htmlBody, null, null);
    }

    public void sendHtmlWithAttachment(String to, String subject, String htmlBody,
                                        byte[] attachmentBytes, String attachmentFilename) {
        send(to, subject, htmlBody, attachmentBytes, attachmentFilename);
    }

    private void send(String to, String subject, String htmlBody, byte[] attachmentBytes, String attachmentFilename) {
        List<Attachment> attachments = attachmentBytes == null
                ? null
                : List.of(new Attachment(attachmentFilename, Base64.getEncoder().encodeToString(attachmentBytes)));

        SendEmailRequest request = new SendEmailRequest(
                properties.getFromAddress(), List.of(to), subject, htmlBody, attachments);

        restClient.post()
                .uri("/emails")
                .body(request)
                .retrieve()
                .toBodilessEntity();
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record SendEmailRequest(
            String from,
            List<String> to,
            String subject,
            String html,
            List<Attachment> attachments
    ) {
    }

    private record Attachment(
            String filename,
            String content
    ) {
    }
}
