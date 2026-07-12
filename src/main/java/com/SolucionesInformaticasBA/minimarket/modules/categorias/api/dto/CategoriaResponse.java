package com.SolucionesInformaticasBA.minimarket.modules.categorias.api.dto;

import java.util.UUID;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CategoriaResponse {
    private UUID id;
    private String nombre;
    private String descripcion;
}
