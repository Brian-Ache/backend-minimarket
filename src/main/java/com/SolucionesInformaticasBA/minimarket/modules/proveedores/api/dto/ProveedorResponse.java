package com.SolucionesInformaticasBA.minimarket.modules.proveedores.api.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProveedorResponse {
    private UUID id;
    private String nombre;
    private String telefono;
    private String email;
    private String direccion;

    /**
     * Null salvo que el proveedor esté dado de baja. Viene con valor desde
     * {@code GET /api/proveedores/v1?incluirBajas=true} y desde el historial de compras y el
     * catálogo, que siguen mostrando al proveedor aunque ya no se pueda operar con él.
     */
    private LocalDateTime deletedAt;
}
