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

    public void sendPasswordResetEmail(User user, String resetUrl) {
        String html = """
                <h2>Recuperacion de contrasena</h2>
                <p>Hola %s, recibimos una solicitud para restablecer la contrasena de tu cuenta.</p>
                <p>Haz clic en el siguiente enlace para crear una nueva contrasena (valido por 24 horas):</p>
                <p><a href="%s" style="background:#b8ff20;color:#000;padding:12px 24px;
                   text-decoration:none;border-radius:8px;font-weight:bold;">
                   Restablecer contrasena</a></p>
                <p>Si no solicitaste esto, ignora este correo. Tu contrasena no cambiara.</p>
                """.formatted(user.getFullName(), resetUrl);

        resendClient.sendHtml(user.getEmail(), "Recuperacion de contrasena - Luxtrox", html);
    }
}
