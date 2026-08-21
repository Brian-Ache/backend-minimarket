package com.SolucionesInformaticasBA.minimarket.modules.usuarios.api.dto;

import com.SolucionesInformaticasBA.minimarket.modules.usuarios.enums.Rol;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Alta por invitación: el administrador carga los datos y la persona recibe un mail para
 * definir su contraseña. No lleva contraseña, justamente: quien invita nunca conoce la del
 * invitado.
 */
@Data
public class InvitarUsuarioRequest {

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

    /**
     * Opcional: si no viene, se deriva de la parte local del email. El formulario de invitación
     * pide lo mínimo —nombre, apellido, email y rol—, y elegir un nombre de usuario no debería
     * ser trabajo de quien invita.
     */
    @Size(min = 1, max = 50)
    @Pattern(regexp = "[^@]*", message = "El nombre de usuario no puede contener @")
    private String username;

    /** Opcional, por defecto EMPLEADO. Tiene que estar por debajo del rol de quien invita. */
    private Rol rol;
}
