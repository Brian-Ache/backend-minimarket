package com.SolucionesInformaticasBA.minimarket.modules.productos.api.dto;

import java.util.UUID;

import jakarta.validation.Valid;
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

    @PositiveOrZero
    private Float costo;

    @PositiveOrZero
    private Float margen;

    private UUID idCategoria;

    private UUID idProveedor;

    /**
     * Existencias con las que nace el producto. Solo se usa en el alta: el PUT no la mira,
     * porque corregir existencias es un ajuste de stock y tiene que quedar en el kardex como
     * tal, no escondido en una edición de catálogo.
     */
    @PositiveOrZero
    private int cantidadInicial;

    /**
     * Primer lote, obligatorio cuando el producto maneja lotes y nace con existencias. Sin
     * esto el alta tenía que terminarse con una segunda llamada, y si esa fallaba quedaba un
     * producto en cero que nadie avisaba.
     */
    @Valid
    private LoteInicialRequest loteInicial;
}
