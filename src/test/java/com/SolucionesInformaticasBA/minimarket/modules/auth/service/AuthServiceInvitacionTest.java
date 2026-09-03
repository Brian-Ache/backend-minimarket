package com.SolucionesInformaticasBA.minimarket.modules.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
import org.springframework.security.crypto.password.PasswordEncoder;

import com.SolucionesInformaticasBA.minimarket.modules.auth.api.dto.AceptarInvitacionRequest;
import com.SolucionesInformaticasBA.minimarket.modules.auth.api.dto.PasswordResetRequest;
import com.SolucionesInformaticasBA.minimarket.modules.auth.entity.AuthToken;
import com.SolucionesInformaticasBA.minimarket.modules.auth.enums.TokenType;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.entity.Usuario;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.enums.EstadoUsuario;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.enums.Rol;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.repository.UsuarioRepository;
import com.SolucionesInformaticasBA.minimarket.security.JwtProvider;
import com.SolucionesInformaticasBA.minimarket.shared.exeption.BadRequestException;
import com.SolucionesInformaticasBA.minimarket.shared.mail.EmailException;
import com.SolucionesInformaticasBA.minimarket.shared.mail.EmailService;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceInvitacionTest {

    @Mock
    private UsuarioRepository userRepository;

    @Mock
    private TokenService tokenService;

    @Mock
    private JwtProvider jwtProvider;

    @Mock
    private PasswordEncoder passwordEncoder;

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
    @DisplayName("aceptar la invitación define la contraseña, activa la cuenta y quema el token")
    void aceptarActivaLaCuenta() {
        Usuario invitado = usuarioPendiente();
        AuthToken token = tokenDeInvitacion(invitado.getId());
        when(tokenService.validateAuthToken("tok", TokenType.INVITATION)).thenReturn(token);
        when(userRepository.findById(invitado.getId())).thenReturn(Optional.of(invitado));
        when(passwordEncoder.encode("MiPassword1!")).thenReturn("hash-nuevo");

        service.aceptarInvitacion(request("tok", "MiPassword1!"));

        assertThat(invitado.getEstado()).isEqualTo(EstadoUsuario.ACTIVO);
        assertThat(invitado.getHashPassword()).isEqualTo("hash-nuevo");
        verify(tokenService).markAuthTokenAsUsed(token.getId());
    }

    @Test
    @DisplayName("un token que no es de invitación no sirve para definir la contraseña")
    void tokenDeOtroTipoNoSirve() {
        when(tokenService.validateAuthToken("tok", TokenType.INVITATION))
                .thenThrow(new BadRequestException("Tipo de token incorrecto"));

        assertThatThrownBy(() -> service.aceptarInvitacion(request("tok", "MiPassword1!")))
                .isInstanceOf(BadRequestException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("una invitación de alguien bloqueado entre medio ya no vale")
    void invitacionDeBloqueadoNoVale() {
        Usuario invitado = usuarioPendiente();
        invitado.setEstado(EstadoUsuario.BLOQUEADO);
        AuthToken token = tokenDeInvitacion(invitado.getId());
        when(tokenService.validateAuthToken("tok", TokenType.INVITATION)).thenReturn(token);
        when(userRepository.findById(invitado.getId())).thenReturn(Optional.of(invitado));

        assertThatThrownBy(() -> service.aceptarInvitacion(request("tok", "MiPassword1!")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("ya no es válida");

        verify(tokenService, never()).markAuthTokenAsUsed(any());
    }

    @Test
    @DisplayName("una invitación de alguien dado de baja tampoco")
    void invitacionDeEliminadoNoVale() {
        Usuario invitado = usuarioPendiente();
        invitado.setDeletedAt(LocalDateTime.now());
        AuthToken token = tokenDeInvitacion(invitado.getId());
        when(tokenService.validateAuthToken("tok", TokenType.INVITATION)).thenReturn(token);
        when(userRepository.findById(invitado.getId())).thenReturn(Optional.of(invitado));

        assertThatThrownBy(() -> service.aceptarInvitacion(request("tok", "MiPassword1!")))
                .isInstanceOf(BadRequestException.class);
    }

    // --- Reseteo de contraseña --------------------------------------------------------------

    @Test
    @DisplayName("el pedido de reseteo manda el mail al email de la cuenta")
    void resetMandaElMail() {
        Usuario u = usuarioPendiente();
        u.setEstado(EstadoUsuario.ACTIVO);
        when(userRepository.findByEmailAndDeletedAtIsNull("ana@ejemplo.com")).thenReturn(Optional.of(u));
        when(tokenService.generatePasswordResetToken(u.getId())).thenReturn("tok-reset");

        service.requestPasswordReset(passwordResetRequest("ana@ejemplo.com"));

        verify(emailService).enviarResetPassword(u.getEmail(), u.getNombre(), "tok-reset",
                TokenService.PASSWORD_RESET_TOKEN_DURATION_HOURS);
    }

    @Test
    @DisplayName("si el SMTP falla, el reseteo responde igual: un 502 delataría qué cuentas existen")
    void falloDeMailEnResetNoPropaga() {
        Usuario u = usuarioPendiente();
        when(userRepository.findByEmailAndDeletedAtIsNull(anyString())).thenReturn(Optional.of(u));
        when(tokenService.generatePasswordResetToken(any())).thenReturn("tok");
        doThrow(new EmailException("smtp caído", null))
                .when(emailService).enviarResetPassword(anyString(), anyString(), anyString(), anyLong());

        assertThatCode(() -> service.requestPasswordReset(passwordResetRequest("ana@ejemplo.com")))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("una cuenta inexistente no manda mail y tampoco falla")
    void cuentaInexistenteNoMandaNada() {
        when(userRepository.findByEmailAndDeletedAtIsNull(anyString())).thenReturn(Optional.empty());
        when(userRepository.findByUsernameAndDeletedAtIsNull(anyString())).thenReturn(Optional.empty());

        assertThatCode(() -> service.requestPasswordReset(passwordResetRequest("nadie@ejemplo.com")))
                .doesNotThrowAnyException();

        verify(emailService, never()).enviarResetPassword(anyString(), anyString(), anyString(), anyLong());
    }

    // --- Helpers ----------------------------------------------------------------------------

    private Usuario usuarioPendiente() {
        return Usuario.builder()
                .id(UUID.randomUUID())
                .nombre("Ana")
                .apellido("Pérez")
                .username("ana")
                .email("ana@ejemplo.com")
                .hashPassword("hash-inutilizable")
                .rol(Rol.EMPLEADO)
                .estado(EstadoUsuario.PENDIENTE)
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
