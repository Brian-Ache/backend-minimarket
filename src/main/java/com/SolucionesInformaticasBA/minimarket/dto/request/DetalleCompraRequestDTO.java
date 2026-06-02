package com.SolucionesInformaticasBA.minimarket.dto.request;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.Data;

@Data
public class DetalleCompraRequestDTO {

    private Long productoId;
    private Integer cantidad;
    private BigDecimal precioUnitario;

    // opcional (lotes)
    private LocalDate fechaVencimiento;
    private String numeroLote;
}