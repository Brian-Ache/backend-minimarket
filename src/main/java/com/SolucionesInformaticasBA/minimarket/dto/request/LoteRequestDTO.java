package com.SolucionesInformaticasBA.minimarket.dto.request;

import java.time.LocalDate;

import lombok.Data;

@Data
public class LoteRequestDTO {

    private Long productoId;
    private String numeroLote;
    private LocalDate fechaVencimiento;
    private Integer cantidad;
}