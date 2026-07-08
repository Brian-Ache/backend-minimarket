package com.SolucionesInformaticasBA.minimarket.modules.auth.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class VerifyEmailRequest {

    @NotBlank
    private String token;
}
