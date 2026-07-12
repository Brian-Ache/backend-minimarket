package com.SolucionesInformaticasBA.minimarket.modules.caja.api.dto;

import java.time.LocalDate;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ResumenCajaResponse {
    private LocalDate fecha;
    private float saldoInicial;
    private float totalVentas;
    private int cantidadVentas;
    private float totalCompras;
    private int cantidadCompras;
    private float totalEntradasManuales;
    private float totalSalidasManuales;
    private float saldoEsperado;
}
