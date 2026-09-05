package com.SolucionesInformaticasBA.minimarket.modules.caja.api.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CorteResponse {
    private UUID id;
    private LocalDateTime fechaApertura;
    private LocalDateTime fechaCierre;
    private float saldoInicial;
    private float saldoEsperado;
    private float saldoReal;
    private float diferencia;

    /** Cuánto se retiró al cerrar y cuánto quedó en la caja. Null en los cortes viejos. */
    private Float montoRetirado;
    private Float saldoDejado;
    private String observaciones;
    private UUID idUsuarioApertura;
    private UUID idUsuarioCierre;
    private ResumenCajaResponse resumen;
}
