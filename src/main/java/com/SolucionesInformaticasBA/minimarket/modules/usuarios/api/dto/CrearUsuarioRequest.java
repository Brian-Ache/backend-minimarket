package com.SolucionesInformaticasBA.minimarket.modules.usuarios.api.dto;

import com.SolucionesInformaticasBA.minimarket.modules.usuarios.enums.Rol;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CrearUsuarioRequest {

    @NotBlank
    @Size(min = 1, max = 50)
    private String nombre;

    @NotBlank
    @Size(min = 1, max = 50)
    private String apellido;

    @NotBlank
    @Email
    @Size(max = 100)
    private String email;

    @NotBlank
    @Size(min = 1, max = 50)
    private String username;

    @NotBlank
    @Size(min = 8, max = 72)
    private String password;

    /** Opcional: si no se indica, se crea como EMPLEADO. */
    private Rol rol;
}
