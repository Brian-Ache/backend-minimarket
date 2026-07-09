package com.SolucionesInformaticasBA.minimarket.modules.productos.api.dto;

import java.util.UUID;

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
    private int stock;
}
