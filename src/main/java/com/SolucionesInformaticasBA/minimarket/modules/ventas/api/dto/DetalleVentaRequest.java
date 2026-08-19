package com.SolucionesInformaticasBA.minimarket.modules.ventas.api.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

@Data
public class DetalleVentaRequest {
    @NotBlank
    private String tipo;            // "PRODUCTO" o "MANUAL"

    @Positive
    private int cantidad;

    private UUID idProducto;        // null si es MANUAL
    private String nombreManual;    // null si es PRODUCTO

    @PositiveOrZero
    private float precioUnitario;   // requerido si MANUAL, ignorado si PRODUCTO
}
