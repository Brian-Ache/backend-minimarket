package com.SolucionesInformaticasBA.minimarket.dto.response;

import java.time.LocalDate;

import lombok.Data;

@Data
public class LoteResponseDTO {

    private Long id;
    private Long productoId;
    private String nombreProducto;

    private String numeroLote;
    private LocalDate fechaVencimiento;

    private Integer cantidad;

    private String estado;
}