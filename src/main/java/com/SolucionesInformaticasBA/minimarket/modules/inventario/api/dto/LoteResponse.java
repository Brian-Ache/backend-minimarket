package com.SolucionesInformaticasBA.minimarket.modules.inventario.api.dto;

import java.time.LocalDate;
import java.util.UUID;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LoteResponse {
    private UUID id;
    private UUID idProducto;
    private String nombreProducto;
    private String numeroLote;
    private LocalDate fechaVencimiento;
    private int cantidad;
    private String estado;
}
