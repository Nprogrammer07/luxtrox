package com.luxtrox.backend.service;

import com.luxtrox.backend.entity.Purchase;
import com.luxtrox.backend.entity.User;
import com.luxtrox.backend.entity.WithdrawalRequest;
import com.luxtrox.backend.integration.email.ResendClient;
import org.springframework.stereotype.Service;

/**
 * Punto unico donde se decide el asunto/contenido de cada correo
 * transaccional. Los templates son HTML simple en linea -- no se trajo
 * un motor de plantillas (Thymeleaf, etc.) a proposito, son pocos
 * correos y bastante cortos para esta fase.
 */
@Service
public class NotificationEmailService {

    private final ResendClient resendClient;

    public NotificationEmailService(ResendClient resendClient) {
        this.resendClient = resendClient;
    }

    public void sendWelcomeEmail(User user) {
        String html = """
                <h2>Bienvenido a Luxtrox Algoritmo, %s</h2>
                <p>Tu cuenta fue creada exitosamente.</p>
                <p>Tu codigo de referido es: <strong>%s</strong></p>
                """.formatted(user.getFullName(), user.getReferralCode());

        resendClient.sendHtml(user.getEmail(), "Bienvenido a Luxtrox Algoritmo", html);
    }

    public void sendPurchaseConfirmedEmail(Purchase purchase, byte[] invoicePdf, String invoiceNumber) {
        User user = purchase.getUser();
        String html = """
                <h2>Tu compra fue confirmada</h2>
                <p>Hola %s, confirmamos tu compra del plan <strong>%s</strong> por $%s USD.</p>
                <p>Adjuntamos tu factura (%s).</p>
                """.formatted(user.getFullName(), purchase.getPlanType().name(),
                purchase.getTotalAmount(), invoiceNumber);

        resendClient.sendHtmlWithAttachment(
                user.getEmail(), "Confirmacion de compra - Luxtrox", html,
                invoicePdf, invoiceNumber + ".pdf");
    }

    public void sendWithdrawalStatusChangedEmail(WithdrawalRequest request, String statusLabel) {
        User user = request.getUser();
        String html = """
                <h2>Actualizacion de tu solicitud de retiro</h2>
                <p>Hola %s, tu solicitud de retiro por $%s USD ahora esta: <strong>%s</strong></p>
                """.formatted(user.getFullName(), request.getAmount(), statusLabel);

        resendClient.sendHtml(user.getEmail(), "Actualizacion de retiro - Luxtrox", html);
    }

    public void sendReferralBonusReceivedEmail(User referrer, java.math.BigDecimal amount) {
        String html = """
                <h2>Recibiste una comision por referido</h2>
                <p>Hola %s, ganaste $%s USD de comision por un referido tuyo. Ya se sumo a tu saldo disponible.</p>
                """.formatted(referrer.getFullName(), amount);

        resendClient.sendHtml(referrer.getEmail(), "Comision de referido recibida - Luxtrox", html);
    }
}
