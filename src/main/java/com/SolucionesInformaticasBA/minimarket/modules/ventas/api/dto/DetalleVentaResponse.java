package com.SolucionesInformaticasBA.minimarket.modules.ventas.api.dto;

import java.util.UUID;

import lombok.Data;

@Data
public class DetalleVentaResponse {
    private UUID idProducto;
    private String nombre;
    private int cantidad;
    private float precioUnitario;
    private float subtotal;
    private String tipo;
}
