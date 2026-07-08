package com.SolucionesInformaticasBA.minimarket.modules.auth.api;

import com.SolucionesInformaticasBA.minimarket.modules.auth.api.dto.AuthResponse;
import com.SolucionesInformaticasBA.minimarket.modules.auth.api.dto.LoginRequest;
import com.SolucionesInformaticasBA.minimarket.modules.auth.api.dto.PasswordResetConfirmRequest;
import com.SolucionesInformaticasBA.minimarket.modules.auth.api.dto.PasswordResetRequest;
import com.SolucionesInformaticasBA.minimarket.modules.auth.api.dto.RefreshTokenRequest;
import com.SolucionesInformaticasBA.minimarket.modules.auth.api.dto.RegisterRequest;
import com.SolucionesInformaticasBA.minimarket.modules.auth.api.dto.VerifyEmailRequest;

public interface AuthApi {

    AuthResponse register(RegisterRequest request);

    AuthResponse login(LoginRequest request);

    AuthResponse refreshToken(RefreshTokenRequest request);

    void logout(String refreshToken);

    void verifyEmail(VerifyEmailRequest request);

    void requestPasswordReset(PasswordResetRequest request);

    void confirmPasswordReset(PasswordResetConfirmRequest request);
}
