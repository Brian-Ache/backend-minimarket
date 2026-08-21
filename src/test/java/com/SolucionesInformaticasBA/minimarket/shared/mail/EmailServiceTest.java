package com.SolucionesInformaticasBA.minimarket.shared.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Properties;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

class EmailServiceTest {

    private JavaMailSender javaMailSender;
    private EmailService emailService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        javaMailSender = mock(JavaMailSender.class);
        when(javaMailSender.createMimeMessage())
                .thenAnswer(inv -> new MimeMessage(Session.getInstance(new Properties())));

        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(javaMailSender);

        emailService = new EmailService(provider);
        configurar("smtp.ejemplo.com");
    }

    private void configurar(String host) {
        ReflectionTestUtils.setField(emailService, "smtpHost", host);
        ReflectionTestUtils.setField(emailService, "remitente", "no-reply@minimarket.local");
        ReflectionTestUtils.setField(emailService, "nombreRemitente", "Minimarket");
        ReflectionTestUtils.setField(emailService, "frontendUrl", "https://app.minimarket.local");
    }

    @Test
    @DisplayName("la invitación lleva el enlace al front con el token")
    void invitacionLlevaElEnlace() throws Exception {
        emailService.enviarInvitacion("nuevo@ejemplo.com", "Ana", "tok3n-abc", 72);

        var mensaje = mensajeEnviado();
        assertThat(cuerpo(mensaje))
                .contains("https://app.minimarket.local/invitacion?token=tok3n-abc")
                .contains("Ana")
                .contains("72");
        assertThat(mensaje.getAllRecipients()[0].toString()).isEqualTo("nuevo@ejemplo.com");
    }

    @Test
    @DisplayName("el token va URL-encodeado en el enlace")
    void tokenEncodeado() throws Exception {
        emailService.enviarResetPassword("alguien@ejemplo.com", "Ana", "a+b/c=", 1);

        assertThat(cuerpo(mensajeEnviado()))
                .contains("/password-reset?token=a%2Bb%2Fc%3D");
    }

    @Test
    @DisplayName("una barra final en la URL del front no duplica la del enlace")
    void barraFinalNoSeDuplica() throws Exception {
        ReflectionTestUtils.setField(emailService, "frontendUrl", "https://app.minimarket.local/");

        emailService.enviarInvitacion("nuevo@ejemplo.com", "Ana", "tok", 72);

        assertThat(cuerpo(mensajeEnviado())).contains("https://app.minimarket.local/invitacion?token=tok");
    }

    @Test
    @DisplayName("sin MAIL_HOST no se manda nada y tampoco se rompe: queda en el log")
    void sinSmtpNoRompe() {
        configurar("");

        assertThatCode(() -> emailService.enviarInvitacion("nuevo@ejemplo.com", "Ana", "tok", 72))
                .doesNotThrowAnyException();

        verify(javaMailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("si el SMTP falla, la excepción sale como EmailException")
    void fallaDeSmtpSeTraduce() {
        doThrow(new MailSendException("smtp caído")).when(javaMailSender).send(any(MimeMessage.class));

        assertThatThrownBy(() -> emailService.enviarInvitacion("nuevo@ejemplo.com", "Ana", "tok", 72))
                .isInstanceOf(EmailException.class)
                .hasMessageContaining("nuevo@ejemplo.com");
    }

    private MimeMessage mensajeEnviado() {
        var captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(javaMailSender).send(captor.capture());
        return captor.getValue();
    }

    private String cuerpo(MimeMessage mensaje) throws Exception {
        return mensaje.getContent().toString();
    }
}
