package com.SolucionesInformaticasBA.minimarket.modules.reportes.api.dto;

import java.util.UUID;

import com.SolucionesInformaticasBA.minimarket.modules.categorias.api.dto.CategoriaResponse;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ReporteInventarioItem {
    private UUID idProducto;
    private String nombre;
    private String barcode;
    private int stockActual;
    private float precio;
    private Float costo;
    private CategoriaResponse categoria;
    private boolean manejaLotes;
}
