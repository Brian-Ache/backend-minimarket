package com.SolucionesInformaticasBA.minimarket.modules.caja.api.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MovimientoCajaResponse {
    private UUID id;
    private UUID idSesion;
    private String tipo;
    private float monto;
    private String motivo;
    private String origen;
    private UUID idReferencia;
    private LocalDateTime fecha;
}
