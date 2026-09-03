package com.SolucionesInformaticasBA.minimarket.modules.auth.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.SolucionesInformaticasBA.minimarket.modules.auth.api.dto.AceptarInvitacionRequest;
import com.SolucionesInformaticasBA.minimarket.modules.auth.api.dto.PasswordResetRequest;
import com.SolucionesInformaticasBA.minimarket.modules.auth.entity.AuthToken;
import com.SolucionesInformaticasBA.minimarket.modules.auth.enums.TokenType;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.api.UsuarioApi;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.api.dto.UsuarioResponse;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.enums.EstadoUsuario;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.enums.Rol;
import com.SolucionesInformaticasBA.minimarket.security.JwtProvider;
import com.SolucionesInformaticasBA.minimarket.shared.exeption.BadRequestException;
import com.SolucionesInformaticasBA.minimarket.shared.mail.EmailException;
import com.SolucionesInformaticasBA.minimarket.shared.mail.EmailService;

/**
 * Lo que auth aporta al alta por invitación: emitir y quemar tokens, y mandar los mails. Que la
 * cuenta quede activa y con la contraseña puesta es asunto del módulo usuarios, y se prueba en
 * {@code UsuarioServiceInvitacionTest}; acá solo se verifica que se le delegue.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceInvitacionTest {

    @Mock
    private UsuarioApi usuarioApi;

    @Mock
    private TokenService tokenService;

    @Mock
    private JwtProvider jwtProvider;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private AuthService service;

    // --- Envío ------------------------------------------------------------------------------

    @Test
    @DisplayName("el envío invalida la invitación anterior antes de emitir la nueva")
    void reenvioInvalidaLaAnterior() {
        var userId = UUID.randomUUID();
        when(tokenService.generateInvitationToken(userId)).thenReturn("tok-nuevo");

        service.enviarInvitacion(userId, "ana@ejemplo.com", "Ana");

        verify(tokenService).invalidateAuthTokens(userId, TokenType.INVITATION);
        verify(emailService).enviarInvitacion("ana@ejemplo.com", "Ana", "tok-nuevo",
                TokenService.INVITATION_TOKEN_DURATION_HOURS);
    }

    @Test
    @DisplayName("si el mail falla, la excepción propaga para voltear el alta que lo disparó")
    void falloDeMailPropaga() {
        var userId = UUID.randomUUID();
        when(tokenService.generateInvitationToken(userId)).thenReturn("tok");
        doThrow(new EmailException("smtp caído", null))
                .when(emailService).enviarInvitacion(anyString(), anyString(), anyString(), anyLong());

        assertThatThrownBy(() -> service.enviarInvitacion(userId, "ana@ejemplo.com", "Ana"))
                .isInstanceOf(EmailException.class);
    }

    // --- Aceptación -------------------------------------------------------------------------

    @Test
    @DisplayName("aceptar delega la contraseña al módulo usuarios y quema el token")
    void aceptarDelegaYQuemaElToken() {
        var userId = UUID.randomUUID();
        AuthToken token = tokenDeInvitacion(userId);
        when(tokenService.validateAuthToken("tok", TokenType.INVITATION)).thenReturn(token);

        service.aceptarInvitacion(request("tok", "MiPassword1!"));

        verify(usuarioApi).establecerPasswordInicial(userId, "MiPassword1!");
        verify(tokenService).markAuthTokenAsUsed(token.getId());
    }

    @Test
    @DisplayName("un token que no es de invitación no llega a tocar al usuario")
    void tokenDeOtroTipoNoSirve() {
        when(tokenService.validateAuthToken("tok", TokenType.INVITATION))
                .thenThrow(new BadRequestException("Tipo de token incorrecto"));

        assertThatThrownBy(() -> service.aceptarInvitacion(request("tok", "MiPassword1!")))
                .isInstanceOf(BadRequestException.class);

        verifyNoInteractions(usuarioApi);
    }

    @Test
    @DisplayName("si usuarios rechaza la invitación, el token no se quema y sigue sirviendo")
    void invitacionRechazadaNoQuemaElToken() {
        var userId = UUID.randomUUID();
        AuthToken token = tokenDeInvitacion(userId);
        when(tokenService.validateAuthToken("tok", TokenType.INVITATION)).thenReturn(token);
        doThrow(new BadRequestException("La invitación ya no es válida"))
                .when(usuarioApi).establecerPasswordInicial(any(), anyString());

        assertThatThrownBy(() -> service.aceptarInvitacion(request("tok", "MiPassword1!")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("ya no es válida");

        verify(tokenService, never()).markAuthTokenAsUsed(any());
    }

    // --- Reseteo de contraseña --------------------------------------------------------------

    @Test
    @DisplayName("el pedido de reseteo manda el mail al email de la cuenta")
    void resetMandaElMail() {
        UsuarioResponse u = usuario();
        when(usuarioApi.buscarPorIdentificador("ana@ejemplo.com")).thenReturn(Optional.of(u));
        when(tokenService.generatePasswordResetToken(u.getId())).thenReturn("tok-reset");

        service.requestPasswordReset(passwordResetRequest("ana@ejemplo.com"));

        verify(emailService).enviarResetPassword(u.getEmail(), u.getNombre(), "tok-reset",
                TokenService.PASSWORD_RESET_TOKEN_DURATION_HOURS);
    }

    @Test
    @DisplayName("si el SMTP falla, el reseteo responde igual: un 502 delataría qué cuentas existen")
    void falloDeMailEnResetNoPropaga() {
        when(usuarioApi.buscarPorIdentificador(anyString())).thenReturn(Optional.of(usuario()));
        when(tokenService.generatePasswordResetToken(any())).thenReturn("tok");
        doThrow(new EmailException("smtp caído", null))
                .when(emailService).enviarResetPassword(anyString(), anyString(), anyString(), anyLong());

        assertThatCode(() -> service.requestPasswordReset(passwordResetRequest("ana@ejemplo.com")))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("una cuenta inexistente no manda mail y tampoco falla")
    void cuentaInexistenteNoMandaNada() {
        when(usuarioApi.buscarPorIdentificador(anyString())).thenReturn(Optional.empty());

        assertThatCode(() -> service.requestPasswordReset(passwordResetRequest("nadie@ejemplo.com")))
                .doesNotThrowAnyException();

        verify(emailService, never()).enviarResetPassword(anyString(), anyString(), anyString(), anyLong());
    }

    // --- Helpers ----------------------------------------------------------------------------

    private UsuarioResponse usuario() {
        return UsuarioResponse.builder()
                .id(UUID.randomUUID())
                .nombre("Ana")
                .apellido("Pérez")
                .username("ana")
                .email("ana@ejemplo.com")
                .rol(Rol.EMPLEADO)
                .estado(EstadoUsuario.ACTIVO)
                .build();
    }

    private AuthToken tokenDeInvitacion(UUID userId) {
        return AuthToken.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .tokenType(TokenType.INVITATION)
                .tokenHash("hash")
                .expiresAt(LocalDateTime.now().plusHours(72))
                .used(false)
                .build();
    }

    private AceptarInvitacionRequest request(String token, String password) {
        var request = new AceptarInvitacionRequest();
        request.setToken(token);
        request.setPassword(password);
        return request;
    }

    private PasswordResetRequest passwordResetRequest(String username) {
        var request = new PasswordResetRequest();
        request.setUsername(username);
        return request;
    }
}
