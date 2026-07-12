package com.SolucionesInformaticasBA.minimarket.modules.productos.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProductoRequest {
    @NotBlank
    @Size(max = 200)
    private String nombre;

    @NotBlank
    @Size(max = 100)
    private String barcode;

    @PositiveOrZero
    private float precio;

    private boolean manejaLotes;

}
