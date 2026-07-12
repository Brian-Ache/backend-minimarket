package com.SolucionesInformaticasBA.minimarket.modules.inventario.api.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MovimientoStockResponse {
    private UUID id;
    private UUID idProducto;
    private int cantidad;
    private String tipo;
    private String motivo;
    private LocalDateTime fecha;
}
