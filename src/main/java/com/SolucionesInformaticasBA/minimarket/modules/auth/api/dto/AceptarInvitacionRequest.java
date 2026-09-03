package com.SolucionesInformaticasBA.minimarket.modules.auth.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Cierre del alta por invitación: el invitado define su contraseña con el token que le llegó
 * por mail, y recién ahí la cuenta pasa a ACTIVO.
 */
@Data
public class AceptarInvitacionRequest {

    @NotBlank
    private String token;

    @NotBlank
    @Size(min = 8, max = 72)
    private String password;
}
