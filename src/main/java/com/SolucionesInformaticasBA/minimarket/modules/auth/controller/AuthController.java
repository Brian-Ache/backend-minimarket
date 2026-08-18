package com.SolucionesInformaticasBA.minimarket.modules.auth.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.SolucionesInformaticasBA.minimarket.modules.auth.api.AuthApi;
import com.SolucionesInformaticasBA.minimarket.modules.auth.api.dto.*;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthApi authApi;

    // El alta de usuarios es exclusiva del ADMIN: POST /api/users/v1

    @PostMapping("/v1/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authApi.login(request));
    }

    @PostMapping("/v1/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(authApi.refreshToken(request));
    }

    @PostMapping("/v1/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
        authApi.logout(request.getRefreshToken());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/v1/verify-email")
    public ResponseEntity<Void> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        authApi.verifyEmail(request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/v1/password-reset")
    public ResponseEntity<Void> requestPasswordReset(@Valid @RequestBody PasswordResetRequest request) {
        authApi.requestPasswordReset(request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/v1/password-reset/confirm")
    public ResponseEntity<Void> confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmRequest request) {
        authApi.confirmPasswordReset(request);
        return ResponseEntity.ok().build();
    }
}
