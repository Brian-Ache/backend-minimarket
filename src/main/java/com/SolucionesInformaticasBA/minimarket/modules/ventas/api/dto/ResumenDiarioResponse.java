package com.SolucionesInformaticasBA.minimarket.modules.ventas.api.dto;

import java.time.LocalDate;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ResumenDiarioResponse {
    private LocalDate fecha;
    private int cantidadVentas;
    private float totalVentas;
    private float totalEfectivo;
    private float totalTarjeta;
    private float totalTransferencia;
}
