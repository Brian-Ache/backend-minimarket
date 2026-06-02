package com.SolucionesInformaticasBA.minimarket.dto.response;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class MovimientoStockResponseDTO {

    private Long id;
    private Long productoId;
    private Integer cantidad;
    private String tipo;
    private String motivo;
    private LocalDateTime fecha;
}