package com.SolucionesInformaticasBA.minimarket.modules.reportes.api.dto;

import java.time.LocalDate;
import java.util.List;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ReporteVentasResponse {
    private LocalDate desde;
    private LocalDate hasta;
    private int totalTransacciones;
    private float totalIngresos;
    private List<VentaDiaria> porDia;

    @Data
    @Builder
    public static class VentaDiaria {
        private LocalDate fecha;
        private int cantidad;
        private float total;
    }
}
