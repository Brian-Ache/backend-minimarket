package com.SolucionesInformaticasBA.minimarket.modules.usuarios.api.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import com.SolucionesInformaticasBA.minimarket.modules.usuarios.enums.EstadoUsuario;
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
    private EstadoUsuario estado;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * Null salvo que la cuenta esté dada de baja. Solo puede venir con valor desde
     * {@code GET /api/users/v1?incluirBajas=true}, que es el único que las devuelve: sin este
     * campo el listado no dejaría distinguir una baja de una cuenta en pie.
     */
    private LocalDateTime deletedAt;
}
