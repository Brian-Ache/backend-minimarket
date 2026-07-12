package com.SolucionesInformaticasBA.minimarket.modules.ventas.api.dto;

import java.util.UUID;

import lombok.Data;

@Data
public class DetalleVentaRequest {
    private String tipo;            // "PRODUCTO" o "MANUAL"
    private int cantidad;
    private UUID idProducto;        // null si es MANUAL
    private String nombreManual;    // null si es PRODUCTO
    private float precioUnitario;   // requerido si MANUAL, ignorado si PRODUCTO
}
