package com.SolucionesInformaticasBA.minimarket.dto.response;

import java.util.UUID;

import lombok.Data;

@Data
public class DetalleVentaResponseDTO {

    private UUID idProducto; // null si es manual

    private String nombre;   // 🔥 SIEMPRE lleno

    private int cantidad;
    private float precioUnitario;
    private float subtotal;

    private String tipo; // 🔥 opcional pero MUY útil
}
