package com.SolucionesInformaticasBA.minimarket.modules.ventas.api.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import lombok.Data;

@Data
public class VentaResponse {
    private UUID id;
    private LocalDateTime fecha;
    private float total;
    private List<DetalleVentaResponse> detalles;
}
