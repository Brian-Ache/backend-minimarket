package com.SolucionesInformaticasBA.minimarket.modules.caja.api.dto;

import java.time.LocalDate;

import lombok.Builder;
import lombok.Data;

/**
 * Desglose de caja. Los totales son objetos y no primitivos a propósito: en los cortes
 * cerrados antes de que el desglose se persistiera viajan como {@code null} ("no se sabe"),
 * que es distinto de 0 ("no hubo movimientos").
 */
@Data
@Builder
public class ResumenCajaResponse {
    private LocalDate fecha;
    private float saldoInicial;
    private Float totalVentas;
    private Integer cantidadVentas;
    private Float totalCompras;
    private Integer cantidadCompras;
    private Float totalEntradasManuales;
    private Float totalSalidasManuales;
    private float saldoEsperado;
}
