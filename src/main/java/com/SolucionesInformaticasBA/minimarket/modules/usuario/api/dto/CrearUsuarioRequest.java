package com.SolucionesInformaticasBA.minimarket.modules.usuario.api.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CrearUsuarioRequest {
    private String username;
    private String password;
}
