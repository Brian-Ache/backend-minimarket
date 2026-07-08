package com.SolucionesInformaticasBA.minimarket.modules.auth.api.dto;

import com.SolucionesInformaticasBA.minimarket.modules.usuarios.api.dto.UsuarioResponse;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;


@Data
@Builder
@AllArgsConstructor
public class AuthResponse {

    private String accessToken;
    private String refreshToken;
    private UsuarioResponse usuario;
}
