package com.SolucionesInformaticasBA.minimarket.modules.auth.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.SolucionesInformaticasBA.minimarket.modules.auth.api.AuthApi;
import com.SolucionesInformaticasBA.minimarket.modules.auth.api.dto.AceptarInvitacionRequest;
import com.SolucionesInformaticasBA.minimarket.modules.auth.api.dto.AuthResponse;
import com.SolucionesInformaticasBA.minimarket.modules.auth.api.dto.InvitacionResponse;
import com.SolucionesInformaticasBA.minimarket.modules.auth.api.dto.LoginRequest;
import com.SolucionesInformaticasBA.minimarket.modules.auth.api.dto.PasswordResetConfirmRequest;
import com.SolucionesInformaticasBA.minimarket.modules.auth.api.dto.PasswordResetRequest;
import com.SolucionesInformaticasBA.minimarket.modules.auth.api.dto.RefreshTokenRequest;
import com.SolucionesInformaticasBA.minimarket.modules.auth.api.dto.VerifyEmailRequest;

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

    /**
     * Datos de la invitación, para que el formulario salude a la persona y le precargue el
     * nombre de usuario sugerido. Público por la misma razón que el POST de abajo.
     *
     * <p>No consume el token: sirve además para que el front distinga un enlace vencido antes
     * de hacerle llenar el formulario.
     */
    @GetMapping("/v1/invitacion")
    public ResponseEntity<InvitacionResponse> consultarInvitacion(@RequestParam String token) {
        return ResponseEntity.ok(authApi.consultarInvitacion(token));
    }

    /**
     * Cierre del alta por invitación. Es público: quien la acepta todavía no tiene contraseña,
     * su credencial es el token que le llegó por mail.
     */
    @PostMapping("/v1/invitacion/aceptar")
    public ResponseEntity<Void> aceptarInvitacion(@Valid @RequestBody AceptarInvitacionRequest request) {
        authApi.aceptarInvitacion(request);
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
