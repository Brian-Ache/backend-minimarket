package com.SolucionesInformaticasBA.minimarket.modules.caja.api.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SesionCajaResponse {
    private UUID id;
    private LocalDateTime fechaApertura;
    private float saldoInicial;
    private String estado;
    private UUID idUsuarioApertura;
}
