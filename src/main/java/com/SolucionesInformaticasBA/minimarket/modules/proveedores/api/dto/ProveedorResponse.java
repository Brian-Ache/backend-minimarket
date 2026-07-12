package com.SolucionesInformaticasBA.minimarket.modules.proveedores.api.dto;

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
}
