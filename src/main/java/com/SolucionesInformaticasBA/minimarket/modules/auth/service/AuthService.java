package com.SolucionesInformaticasBA.minimarket.modules.auth.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.SolucionesInformaticasBA.minimarket.modules.auth.api.AuthApi;
import com.SolucionesInformaticasBA.minimarket.modules.auth.api.dto.AceptarInvitacionRequest;
import com.SolucionesInformaticasBA.minimarket.modules.auth.api.dto.AuthResponse;
import com.SolucionesInformaticasBA.minimarket.modules.auth.api.dto.InvitacionResponse;
import com.SolucionesInformaticasBA.minimarket.modules.auth.api.dto.LoginRequest;
import com.SolucionesInformaticasBA.minimarket.modules.auth.api.dto.PasswordResetConfirmRequest;
import com.SolucionesInformaticasBA.minimarket.modules.auth.api.dto.PasswordResetRequest;
import com.SolucionesInformaticasBA.minimarket.modules.auth.api.dto.RefreshTokenRequest;
import com.SolucionesInformaticasBA.minimarket.modules.auth.entity.AuthToken;
import com.SolucionesInformaticasBA.minimarket.modules.auth.entity.RefreshToken;
import com.SolucionesInformaticasBA.minimarket.modules.auth.enums.TokenType;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.api.UsuarioApi;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.api.dto.UsuarioResponse;
import com.SolucionesInformaticasBA.minimarket.security.JwtProvider;
import com.SolucionesInformaticasBA.minimarket.shared.exeption.UnauthorizedException;
import com.SolucionesInformaticasBA.minimarket.shared.mail.EmailException;
import com.SolucionesInformaticasBA.minimarket.shared.mail.EmailService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Sesiones, tokens y los mails que los transportan.
 *
 * <p>De usuarios no conoce nada más que {@link UsuarioApi} y su {@link UsuarioResponse}: quién
 * puede entrar, cómo se guarda una contraseña y cuándo una cuenta queda habilitada son
 * decisiones de aquel módulo, y acá solo se piden. Por eso este servicio no tiene un
 * {@code PasswordEncoder} ni toca la entidad {@code Usuario}.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AuthService implements AuthApi {

    private final UsuarioApi usuarioApi;
    private final TokenService tokenService;
    private final JwtProvider jwtProvider;
    private final EmailService emailService;

    /**
     * Acepta email o nombre de usuario, indistinto.
     *
     * <p>Un solo mensaje para todos los rechazos: cuenta inexistente, cuenta sin acceso y
     * contraseña equivocada responden igual, para no filtrar qué cuentas existen ni en qué
     * estado están. La distinción tampoco llega hasta acá — {@code verificarCredenciales}
     * devuelve vacío en los tres casos.
     */
    @Override
    public AuthResponse login(LoginRequest request) {
        UsuarioResponse u = usuarioApi
                .verificarCredenciales(request.getUsername(), request.getPassword())
                .orElseThrow(() -> new UnauthorizedException("Credenciales inválidas"));

        String accessToken = jwtProvider.generateAccessToken(u.getId(), u.getRol());
        String refreshToken = tokenService.generateRefreshToken(u.getId());

        return toResponse(accessToken, refreshToken, u);
    }

    @Override
    @Transactional
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        RefreshToken refreshToken = tokenService.validateRefreshToken(request.getRefreshToken());

        tokenService.revokeRefreshToken(request.getRefreshToken());

        UsuarioResponse u = usuarioApi.getById(refreshToken.getUserId());

        String accessToken = jwtProvider.generateAccessToken(u.getId(), u.getRol());
        String newRefreshToken = tokenService.generateRefreshToken(u.getId());

        return toResponse(accessToken, newRefreshToken, u);
    }

    @Override
    public void logout(String refreshToken) {
        tokenService.revokeRefreshToken(refreshToken);
    }

    @Override
    public void revokeAllSessions(UUID userId) {
        int revocadas = tokenService.revokeAllUserRefreshTokens(userId);
        log.info("Se revocaron {} sesiones del usuario {}", revocadas, userId);
    }

    @Override
    @Transactional
    public void enviarInvitacion(UUID userId, String email, String nombre) {
        // Un reenvío no puede dejar viva la invitación anterior: sería otra puerta abierta
        // hasta que expire.
        tokenService.invalidateAuthTokens(userId, TokenType.INVITATION);

        String token = tokenService.generateInvitationToken(userId);

        // Si el mail falla, la excepción propaga y voltea la transacción del alta: preferimos
        // no tener el usuario a tenerlo sin que nadie pueda avisarle.
        emailService.enviarInvitacion(email, nombre, token,
                TokenService.INVITATION_TOKEN_DURATION_HOURS);

        log.info("Invitación enviada a {}", email);
    }

    /**
     * Valida el token sin quemarlo: es una consulta, y la persona todavía no completó nada. El
     * token se marca usado recién en {@link #aceptarInvitacion}.
     */
    @Override
    public InvitacionResponse consultarInvitacion(String token) {
        AuthToken authToken = tokenService.validateAuthToken(token, TokenType.INVITATION);

        UsuarioResponse u = usuarioApi.getCuentaInvitada(authToken.getUserId());

        return InvitacionResponse.builder()
                .nombre(u.getNombre())
                .apellido(u.getApellido())
                .email(u.getEmail())
                .usernameSugerido(u.getUsername())
                .build();
    }

    /**
     * El token prueba quién es; definir la contraseña y el nombre de usuario, y habilitar la
     * cuenta, es de usuarios, que además decide si la invitación sigue en pie —la cuenta pudo
     * darse de baja o bloquearse entre el envío y la aceptación—.
     */
    @Override
    @Transactional
    public void aceptarInvitacion(AceptarInvitacionRequest request) {
        AuthToken authToken = tokenService.validateAuthToken(request.getToken(), TokenType.INVITATION);

        usuarioApi.establecerPasswordInicial(
                authToken.getUserId(), request.getPassword(), request.getUsername());
        tokenService.markAuthTokenAsUsed(authToken.getId());

        log.info("Invitación aceptada por el usuario {}", authToken.getUserId());
    }

    /**
     * Acepta email o username, igual que el login: quien entra con su nombre de usuario va a
     * escribir eso mismo acá. El mail de recuperación se manda de todos modos a su email.
     *
     * <p>Responde igual exista o no la cuenta, para no revelar qué emails están registrados. Por
     * eso, a diferencia de la invitación, un fallo de SMTP se traga y se loguea: devolver 502
     * solo cuando la cuenta existe delataría cuáles existen.
     */
    @Override
    @Transactional
    public void requestPasswordReset(PasswordResetRequest request) {
        usuarioApi.buscarPorIdentificador(request.getUsername())
                .ifPresent(u -> {
                    String token = tokenService.generatePasswordResetToken(u.getId());
                    try {
                        emailService.enviarResetPassword(u.getEmail(), u.getNombre(), token,
                                TokenService.PASSWORD_RESET_TOKEN_DURATION_HOURS);
                    } catch (EmailException e) {
                        log.error("No se pudo enviar el reseteo de contraseña a {}", u.getEmail(), e);
                    }
                });
    }

    @Override
    @Transactional
    public void confirmPasswordReset(PasswordResetConfirmRequest request) {
        AuthToken authToken = tokenService.validateAuthToken(request.getToken(), TokenType.PASSWORD_RESET);

        usuarioApi.restablecerPassword(authToken.getUserId(), request.getNewPassword());
        tokenService.markAuthTokenAsUsed(authToken.getId());

        // La contraseña cambió, así que las sesiones abiertas con la anterior dejan de valer.
        tokenService.revokeAllUserRefreshTokens(authToken.getUserId());
    }

    private AuthResponse toResponse(String accessToken, String newRefreshToken, UsuarioResponse usuario) {
        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(newRefreshToken)
                .usuario(usuario)
                .build();
    }
}
