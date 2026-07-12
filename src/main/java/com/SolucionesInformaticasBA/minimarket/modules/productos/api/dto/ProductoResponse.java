package com.SolucionesInformaticasBA.minimarket.modules.productos.api.dto;

import java.util.UUID;

import com.SolucionesInformaticasBA.minimarket.modules.categorias.api.dto.CategoriaResponse;
import com.SolucionesInformaticasBA.minimarket.modules.proveedores.api.dto.ProveedorResponse;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProductoResponse {
    private UUID id;
    private String nombre;
    private String barcode;
    private float precio;
    private boolean manejaLotes;
    private Float costo;
    private Float margen;
    private CategoriaResponse categoria;
    private ProveedorResponse proveedor;
}
