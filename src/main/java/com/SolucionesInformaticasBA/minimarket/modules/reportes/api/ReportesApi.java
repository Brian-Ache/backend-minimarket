package com.SolucionesInformaticasBA.minimarket.modules.reportes.api;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.SolucionesInformaticasBA.minimarket.modules.reportes.api.dto.ProductoMasVendidoResponse;
import com.SolucionesInformaticasBA.minimarket.modules.reportes.api.dto.ReporteGananciasResponse;
import com.SolucionesInformaticasBA.minimarket.modules.reportes.api.dto.ReporteInventarioItem;
import com.SolucionesInformaticasBA.minimarket.modules.reportes.api.dto.ReporteVentasResponse;

public interface ReportesApi {
    ReporteVentasResponse getReporteVentas(LocalDate desde, LocalDate hasta);
    ReporteGananciasResponse getReporteGanancias(LocalDate desde, LocalDate hasta);
    /**
     * Existencias y valorización del catálogo, paginado. Devolvía el catálogo entero en cada
     * request, resolviendo además la categoría de cada fila.
     */
    Page<ReporteInventarioItem> getReporteInventario(Pageable pageable);
    List<ProductoMasVendidoResponse> getProductosMasVendidos(LocalDate desde, LocalDate hasta, int limite);
}
