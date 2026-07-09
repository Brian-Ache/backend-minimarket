package com.SolucionesInformaticasBA.minimarket.dto.response;

import java.time.LocalDate;
import java.util.UUID;

import lombok.Data;

@Data
public class LoteResponseDTO {

    private UUID id;
    private UUID idProducto;
    private String nombreProducto;

    private String numeroLote;
    private LocalDate fechaVencimiento;

    private int cantidad;

    private String estado;
}