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

    // Sesión

    AuthResponse login(LoginRequest request);

    AuthResponse refreshToken(RefreshTokenRequest request);

    void logout(String refreshToken);

    // Cierra todas las sesiones abiertas de un usuario (baja, bloqueo, cambio de rol). 
    void revokeAllSessions(UUID userId);

    // Invitaciones

    void enviarInvitacion(UUID userId, String email, String nombre);

    InvitacionResponse consultarInvitacion(String token);

    // Define contraseña y nombre de usuario del invitado, y activa la cuenta. 
    void aceptarInvitacion(AceptarInvitacionRequest request);

    // Reseteo de contraseña 

    void requestPasswordReset(PasswordResetRequest request);

    void confirmPasswordReset(PasswordResetConfirmRequest request);
}