package com.SolucionesInformaticasBA.minimarket.dto.response;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import lombok.Data;

@Data
public class VentaResponseDTO {

    private UUID id;
    private LocalDateTime fecha;
    private float total;

    private List<DetalleVentaResponseDTO> detalles;
}