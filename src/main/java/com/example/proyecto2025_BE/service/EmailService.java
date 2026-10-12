package com.example.proyecto2025_BE.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Envío de emails transaccionales. Todos los métodos son @Async y tragan sus propios
 * errores: si el SMTP no está configurado la respuesta al usuario no cambia.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from:no-reply@triviando.com.ar}")
    private String from;

    @Value("${app.mail.from-name:TrivIAndo}")
    private String fromName;

    @Async
    public void sendPasswordResetCode(String to, String code) {
        String subject = "Tu código de recuperación de TrivIAndo";
        String html = layout("""
                <p>Hola,</p>
                <p>Recibiste este código porque se pidió recuperar la contraseña de tu cuenta de TrivIAndo.</p>
                <div class="code">%s</div>
                <p class="muted">Vence en 15 minutos. Si no pediste este código, ignorá este email:
                tu contraseña no cambió.</p>
                """.formatted(code));
        send(to, subject, html);
    }

    @Async
    public void sendGoogleAccountNotice(String to) {
        String subject = "Tu cuenta de TrivIAndo usa Google";
        String html = layout("""
                <p>Hola,</p>
                <p>Pediste recuperar la contraseña de tu cuenta de TrivIAndo, pero esa cuenta se creó
                con Google: la contraseña la gestionás desde tu cuenta de Google.</p>
                <p>Para entrar, usá el botón <strong>Continuar con Google</strong> en la pantalla de
                inicio de sesión.</p>
                <p class="muted">Tu contraseña de TrivIAndo no cambió.</p>
                """);
        send(to, subject, html);
    }

    private void send(String to, String subject, String html) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from, fromName);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(message);
            log.info("Email enviado a {}", to);
        } catch (Exception exception) {
            // Nunca propagamos: la respuesta al usuario debe ser idéntica falle o no el SMTP.
            log.error("No se pudo enviar el email a {}: {}", to, exception.getMessage());
        }
    }

    private String layout(String body) {
        return """
                <!DOCTYPE html>
                <html>
                <body style="margin:0;background:#f0fafc;font-family:Arial,Helvetica,sans-serif;">
                  <div style="max-width:520px;margin:24px auto;background:#ffffff;border-radius:12px;
                              overflow:hidden;border:1px solid #e0f2f7;">
                    <div style="background:linear-gradient(90deg,#14b8a6,#06b6d4);padding:20px 28px;">
                      <span style="color:#ffffff;font-size:20px;font-weight:bold;">TrivIAndo</span>
                    </div>
                    <div style="padding:28px;color:#1f2937;font-size:15px;line-height:1.6;">
                      %s
                      <p class="muted" style="color:#6b7280;font-size:13px;">
                        Si tenés problemas, respondé este email y te ayudamos.
                      </p>
                    </div>
                  </div>
                </body>
                </html>
                """.formatted("""
                <style>
                  .code { font-size:32px; letter-spacing:12px; font-weight:bold; text-align:center;
                          color:#0e7490; background:#ecfeff; border:1px dashed #22d3ee;
                          border-radius:10px; padding:16px; margin:20px 0; }
                  .muted { color:#6b7280; font-size:13px; }
                </style>
                """ + body);
    }
}
