package com.SolucionesInformaticasBA.minimarket.modules.reportes.api.dto;

import java.time.LocalDate;
import java.util.List;

import lombok.Builder;
import lombok.Data;

/**
 * Ganancia del período, calculada como margen sobre lo vendido y cobrado.
 *
 * <p>No confundir con flujo de caja: {@code totalCompras} es lo que se gastó reponiendo
 * mercadería en el período y se informa aparte, pero no entra en la ganancia. Restarlo daría
 * pérdida cada vez que se hace una compra grande, aunque el negocio haya ganado plata.
 */
@Data
@Builder
public class ReporteGananciasResponse {
    private LocalDate desde;
    private LocalDate hasta;

    /** Ventas cobradas en el período. */
    private float totalVentas;

    /** Costo de la mercadería efectivamente vendida (congelado al momento de cada venta). */
    private float costoMercaderiaVendida;

    /** totalVentas - costoMercaderiaVendida */
    private float gananciaBruta;

    /** Compras del período. Informativo: es flujo de caja, no costo de lo vendido. */
    private float totalCompras;

    /** Unidades vendidas sin costo conocido (ítems manuales o productos sin costo cargado). */
    private int unidadesSinCosto;

    private List<GananciaDiaria> porDia;

    @Data
    @Builder
    public static class GananciaDiaria {
        private LocalDate fecha;
        private float ventas;
        private float costo;
        private float ganancia;
        private float compras;
    }
}
