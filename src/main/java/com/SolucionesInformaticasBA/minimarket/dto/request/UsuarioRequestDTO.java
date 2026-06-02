package com.SolucionesInformaticasBA.minimarket.dto.request;

import lombok.Data;

@Data
public class UsuarioRequestDTO {

    private String username;
    private String password;
    private String rol;
}