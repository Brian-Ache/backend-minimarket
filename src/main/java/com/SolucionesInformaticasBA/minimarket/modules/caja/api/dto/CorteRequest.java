package com.SolucionesInformaticasBA.minimarket.modules.caja.api.dto;

import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CorteRequest {
    @PositiveOrZero
    private float saldoReal;

    /**
     * Cuánto se retira de la caja al cerrar. Lo que no se retira queda para el turno siguiente,
     * que lo declara al abrir. Sin este dato, el resumen del día volvía a sumar esa misma plata
     * como saldo inicial del turno que venía.
     */
    @PositiveOrZero
    private float montoRetirado;

    @Size(max = 255)
    private String observaciones;
}
