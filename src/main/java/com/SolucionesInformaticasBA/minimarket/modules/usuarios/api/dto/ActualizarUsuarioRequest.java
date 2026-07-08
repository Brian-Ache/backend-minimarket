package com.SolucionesInformaticasBA.minimarket.modules.usuarios.api.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ActualizarUsuarioRequest {

    @Size(max = 50)
    private String nombre;

    @Size(max = 50)
    private String apellido;
}
