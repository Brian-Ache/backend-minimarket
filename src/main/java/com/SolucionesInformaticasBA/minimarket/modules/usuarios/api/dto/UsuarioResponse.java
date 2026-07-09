package com.SolucionesInformaticasBA.minimarket.modules.usuarios.api.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import com.SolucionesInformaticasBA.minimarket.modules.usuarios.enums.Rol;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class UsuarioResponse {

    private UUID id;
    private String nombre;
    private String apellido;
    private String username;
    private String email;
    private Rol rol;
    private boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
