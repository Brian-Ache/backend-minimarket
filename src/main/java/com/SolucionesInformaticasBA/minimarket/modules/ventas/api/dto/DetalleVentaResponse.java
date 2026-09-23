package com.SolucionesInformaticasBA.minimarket.modules.ventas.api.dto;

import java.math.BigDecimal;

import java.util.UUID;

import lombok.Data;

@Data
public class DetalleVentaResponse {
    private UUID idProducto;
    private String nombre;
    private int cantidad;
    private BigDecimal precioUnitario;
    private BigDecimal subtotal;
    private String tipo;

    /** Costo congelado al momento de la venta. Null en ítems manuales o sin costo cargado. */
    private BigDecimal costoUnitario;
}
