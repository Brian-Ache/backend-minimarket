package com.SolucionesInformaticasBA.minimarket.modules.auth.api;

import java.util.UUID;

import com.SolucionesInformaticasBA.minimarket.modules.auth.api.dto.AceptarInvitacionRequest;
import com.SolucionesInformaticasBA.minimarket.modules.auth.api.dto.AuthResponse;
import com.SolucionesInformaticasBA.minimarket.modules.auth.api.dto.InvitacionResponse;
import com.SolucionesInformaticasBA.minimarket.modules.auth.api.dto.LoginRequest;
import com.SolucionesInformaticasBA.minimarket.modules.auth.api.dto.PasswordResetConfirmRequest;
import com.SolucionesInformaticasBA.minimarket.modules.auth.api.dto.PasswordResetRequest;
import com.SolucionesInformaticasBA.minimarket.modules.auth.api.dto.RefreshTokenRequest;

public interface AuthApi {
    AuthResponse login(LoginRequest request);

    AuthResponse refreshToken(RefreshTokenRequest request);

    void logout(String refreshToken);

    /** Cierra todas las sesiones abiertas de un usuario (baja, bloqueo, cambio de rol). */
    void revokeAllSessions(UUID userId);

    /**
     * Emite el token de invitación y manda el mail. Invalida cualquier invitación anterior sin
     * usar, así que sirve igual para el primer envío y para el reenvío.
     *
     * <p>Lo llama el módulo de usuarios después de crear la cuenta en estado PENDIENTE: el alta
     * es asunto suyo, los tokens y el mail son de acá.
     *
     * @throws com.SolucionesInformaticasBA.minimarket.shared.mail.EmailException si el envío
     *         falla, para que el alta que lo disparó no quede confirmada sin haber avisado.
     */
    void enviarInvitacion(UUID userId, String email, String nombre);

    /**
     * Datos de la invitación detrás de un token, sin consumirlo. Lo llama el formulario de
     * aceptación al abrirse, para no pedirle los datos a alguien cuyo enlace ya venció.
     */
    InvitacionResponse consultarInvitacion(String token);

    /** Define la contraseña y el nombre de usuario del invitado, y activa la cuenta. */
    void aceptarInvitacion(AceptarInvitacionRequest request);

    void requestPasswordReset(PasswordResetRequest request);

    void confirmPasswordReset(PasswordResetConfirmRequest request);
}
