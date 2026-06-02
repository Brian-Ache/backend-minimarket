package com.SolucionesInformaticasBA.minimarket.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import lombok.Data;

@Data
public class VentaResponseDTO {

    private Long id;
    private LocalDateTime fecha;
    private BigDecimal total;

    private List<DetalleVentaResponseDTO> detalles;
}