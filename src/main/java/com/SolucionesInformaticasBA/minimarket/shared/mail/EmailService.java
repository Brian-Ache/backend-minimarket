package com.SolucionesInformaticasBA.minimarket.shared.mail;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Envío de los mails del sistema: invitaciones y reseteo de contraseña.
 *
 * <p>Los textos viven acá, en un solo lugar, en vez de repartidos por los servicios que los
 * disparan. Son texto plano a propósito: el contenido es un saludo y un enlace, y un HTML
 * traería una plantilla más que mantener sin agregar nada.
 *
 * <p><b>Sin SMTP configurado</b> (sin {@code MAIL_HOST}) no se manda nada: el mail queda en el
 * log, enlace incluido. Es lo que hace que el sistema levante en desarrollo sin un servidor de
 * correo, y reemplaza al viejo {@code log.debug} del token suelto.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class EmailService {

    private final ObjectProvider<JavaMailSender> javaMailSender;

    @Value("${spring.mail.host:}")
    private String smtpHost;

    @Value("${app.mail.remitente}")
    private String remitente;

    @Value("${app.mail.nombre-remitente}")
    private String nombreRemitente;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    /**
     * Invitación a sumarse al sistema. El enlace lleva al formulario donde define su contraseña;
     * hasta que lo haga, la cuenta existe pero está en estado PENDIENTE y no puede entrar.
     */
    public void enviarInvitacion(String destinatario, String nombre, String token, long validezHoras) {
        var enlace = enlaceCon("/invitacion", token);

        enviar(destinatario,
                "Te invitaron a " + nombreRemitente,
                """
                Hola %s:

                Te dieron de alta en %s. Para activar tu cuenta y elegir tu contraseña, entrá acá:

                %s

                El enlace vence en %d horas. Si no lo esperabas, ignorá este mensaje: sin definir
                la contraseña la cuenta no se activa.
                """.formatted(nombre, nombreRemitente, enlace, validezHoras));
    }

    /**
     * Reseteo de contraseña. Se manda siempre al email de la cuenta, aunque el pedido haya
     * llegado con el nombre de usuario.
     */
    public void enviarResetPassword(String destinatario, String nombre, String token, long validezHoras) {
        var enlace = enlaceCon("/password-reset", token);

        enviar(destinatario,
                "Restablecer tu contraseña de " + nombreRemitente,
                """
                Hola %s:

                Pediste restablecer tu contraseña. Elegí una nueva acá:

                %s

                El enlace vence en %d hora(s) y sirve una sola vez. Si no fuiste vos, ignorá este
                mensaje: tu contraseña actual sigue funcionando.
                """.formatted(nombre, enlace, validezHoras));
    }

    /**
     * @throws EmailException si el SMTP está configurado y el envío falla. Quien invita necesita
     *                        enterarse: un alta cuyo mail nunca salió deja a la persona sin forma
     *                        de entrar y al administrador creyendo que ya está.
     */
    private void enviar(String destinatario, String asunto, String cuerpo) {
        JavaMailSender sender = smtpHost.isBlank() ? null : javaMailSender.getIfAvailable();

        if (sender == null) {
            log.warn("""
                    SMTP no configurado (falta MAIL_HOST): el mail no se envió.
                    Para: {}
                    Asunto: {}
                    {}""", destinatario, asunto, cuerpo);
            return;
        }

        try {
            var mensaje = sender.createMimeMessage();
            var helper = new MimeMessageHelper(mensaje, false, StandardCharsets.UTF_8.name());

            helper.setFrom(remitente, nombreRemitente);
            helper.setTo(destinatario);
            helper.setSubject(asunto);
            helper.setText(cuerpo);

            sender.send(mensaje);
            log.info("Mail enviado a {}: {}", destinatario, asunto);
        } catch (MailException | jakarta.mail.MessagingException | UnsupportedEncodingException e) {
            log.error("Falló el envío del mail a {}: {}", destinatario, e.getMessage());
            throw new EmailException("No se pudo enviar el email a " + destinatario, e);
        }
    }

    /**
     * El token va como query param y no como parte del path: así el front lo lee igual aunque
     * cambie el ruteo, y queda URL-encoded sin depender de cómo esté escrito.
     */
    private String enlaceCon(String ruta, String token) {
        var base = frontendUrl.endsWith("/")
                ? frontendUrl.substring(0, frontendUrl.length() - 1)
                : frontendUrl;

        return base + ruta + "?token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
    }
}
