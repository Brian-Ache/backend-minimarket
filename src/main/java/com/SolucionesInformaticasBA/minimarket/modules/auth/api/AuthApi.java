package com.SolucionesInformaticasBA.minimarket.modules.auth.api;

import com.SolucionesInformaticasBA.minimarket.modules.auth.api.dto.AuthResponse;
import com.SolucionesInformaticasBA.minimarket.modules.auth.api.dto.LoginRequest;
import com.SolucionesInformaticasBA.minimarket.modules.auth.api.dto.PasswordResetConfirmRequest;
import com.SolucionesInformaticasBA.minimarket.modules.auth.api.dto.PasswordResetRequest;
import com.SolucionesInformaticasBA.minimarket.modules.auth.api.dto.RefreshTokenRequest;
import com.SolucionesInformaticasBA.minimarket.modules.auth.api.dto.RegisterRequest;
import com.SolucionesInformaticasBA.minimarket.modules.auth.api.dto.VerifyEmailRequest;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.api.dto.UsuarioResponse;

public interface AuthApi {

    /**
     * Alta autogestionada: crea el usuario deshabilitado y emite el token de verificación.
     *
     * <p><b>Sin endpoint a propósito.</b> Hoy el alta es exclusiva del ADMIN
     * (POST /api/users/v1). Para habilitar el autorregistro hace falta: (1) exponerla en
     * {@code AuthController}, (2) agregar el envío de mail con el token de verificación, y
     * (3) permitir la ruta en {@code SecurityConfig}. El usuario queda con
     * {@code enabled=false} hasta que confirme con {@link #verifyEmail}.
     */
    UsuarioResponse register(RegisterRequest request);

    AuthResponse login(LoginRequest request);

    AuthResponse refreshToken(RefreshTokenRequest request);

    void logout(String refreshToken);

    void verifyEmail(VerifyEmailRequest request);

    void requestPasswordReset(PasswordResetRequest request);

    void confirmPasswordReset(PasswordResetConfirmRequest request);
}
