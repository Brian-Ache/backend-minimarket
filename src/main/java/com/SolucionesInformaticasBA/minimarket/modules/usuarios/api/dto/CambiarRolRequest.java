package com.SolucionesInformaticasBA.minimarket.modules.usuarios.api.dto;

import com.SolucionesInformaticasBA.minimarket.modules.usuarios.enums.Rol;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CambiarRolRequest {

    /**
     * Rol nuevo. Que el valor exista en el enum no alcanza: tiene que estar por debajo del rol
     * de quien pide el cambio, así que mandar {@code SUPERADMIN} siempre da 403.
     */
    @NotNull
    private Rol rol;
}
