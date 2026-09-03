package com.SolucionesInformaticasBA.minimarket.modules.auth.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Cierre del alta por invitación: el invitado define su contraseña —y, si quiere, su nombre de
 * usuario— con el token que le llegó por mail, y recién ahí la cuenta pasa a ACTIVO.
 */
@Data
public class AceptarInvitacionRequest {

    @NotBlank
    private String token;

    @NotBlank
    @Size(min = 8, max = 72)
    private String password;

    /**
     * Opcional: si no viene, queda el que se derivó del email al invitarlo. El formulario lo
     * muestra precargado —lo trae {@code GET /api/auth/v1/invitacion}— y el invitado puede
     * cambiarlo antes de confirmar.
     */
    @Size(min = 1, max = 50)
    @Pattern(regexp = "[^@]*", message = "El nombre de usuario no puede contener @")
    private String username;
}
