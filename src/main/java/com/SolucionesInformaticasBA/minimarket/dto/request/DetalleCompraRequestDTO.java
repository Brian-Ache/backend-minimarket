package com.SolucionesInformaticasBA.minimarket.dto.request;

import java.time.LocalDate;
import java.util.UUID;

import lombok.Data;

@Data
public class DetalleCompraRequestDTO {

    private UUID idProducto;
    private int cantidad;
    private float precioUnitario;

    // opcional (lotes)
    private LocalDate fechaVencimiento;
    private String numeroLote;
}