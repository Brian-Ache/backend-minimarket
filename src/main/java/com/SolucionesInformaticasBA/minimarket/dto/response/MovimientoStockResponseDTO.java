package com.SolucionesInformaticasBA.minimarket.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

import lombok.Data;

@Data
public class MovimientoStockResponseDTO {

    private UUID id;
    private UUID idProducto;
    private int cantidad;
    private String tipo;
    private String motivo;
    private LocalDateTime fecha;
}