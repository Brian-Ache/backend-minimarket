package com.SolucionesInformaticasBA.minimarket.modules.auth.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {

    /**
     * Email o nombre de usuario, indistinto.
     *
     * <p>El campo conserva el nombre {@code username} para no romper el contrato con el front,
     * que ya lo venía enviando (con el email adentro, pese al nombre).
     */
    @NotBlank
    private String username;

    @NotBlank
    private String password;
}
