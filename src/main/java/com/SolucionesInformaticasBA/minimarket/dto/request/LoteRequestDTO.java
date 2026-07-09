package com.SolucionesInformaticasBA.minimarket.dto.request;

import java.time.LocalDate;
import java.util.UUID;

import lombok.Data;

@Data
public class LoteRequestDTO {

    private UUID idProducto;
    private String numeroLote;
    private LocalDate fechaVencimiento;
    private int cantidad;
}