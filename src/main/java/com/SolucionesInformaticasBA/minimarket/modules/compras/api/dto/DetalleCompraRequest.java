package com.SolucionesInformaticasBA.minimarket.modules.compras.api.dto;

import java.time.LocalDate;
import java.util.UUID;

import lombok.*;

@Data
@Builder
public class DetalleCompraRequest {
    private UUID idProducto;
    private float precioUnitario;
    private int cantidad;

    private Float margen;
    private Float precioVenta;

    // opcional para lotes, pueden ser null
    private LocalDate fechaVencimiento;
    private String numeroLote;
}
