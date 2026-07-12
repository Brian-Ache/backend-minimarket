package com.SolucionesInformaticasBA.minimarket.modules.reportes.api.dto;

import java.time.LocalDate;
import java.util.List;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ReporteGananciasResponse {
    private LocalDate desde;
    private LocalDate hasta;
    private float totalVentas;
    private float totalCompras;
    private float gananciaBruta;
    private List<GananciaDiaria> porDia;

    @Data
    @Builder
    public static class GananciaDiaria {
        private LocalDate fecha;
        private float ventas;
        private float compras;
        private float ganancia;
    }
}
