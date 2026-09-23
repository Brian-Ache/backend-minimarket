package com.SolucionesInformaticasBA.minimarket.modules.auth.api.dto;

import com.SolucionesInformaticasBA.minimarket.shared.validation.Password;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PasswordResetConfirmRequest {

    @NotBlank
    private String token;

    @NotBlank
    @Password
    private String newPassword;
}
