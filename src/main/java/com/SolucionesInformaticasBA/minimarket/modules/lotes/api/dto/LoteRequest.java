package com.SolucionesInformaticasBA.minimarket.modules.lotes.api.dto;

import java.time.LocalDate;
import java.util.UUID;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LoteRequest {
    private UUID idProducto;
    private String numeroLote;
    private LocalDate fechaVencimiento;
    private int cantidad;
}
