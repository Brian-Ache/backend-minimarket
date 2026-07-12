package com.SolucionesInformaticasBA.minimarket.modules.reportes.api;

import java.time.LocalDate;
import java.util.List;

import com.SolucionesInformaticasBA.minimarket.modules.reportes.api.dto.ProductoMasVendidoResponse;
import com.SolucionesInformaticasBA.minimarket.modules.reportes.api.dto.ReporteGananciasResponse;
import com.SolucionesInformaticasBA.minimarket.modules.reportes.api.dto.ReporteInventarioItem;
import com.SolucionesInformaticasBA.minimarket.modules.reportes.api.dto.ReporteVentasResponse;

public interface ReportesApi {
    ReporteVentasResponse getReporteVentas(LocalDate desde, LocalDate hasta);
    ReporteGananciasResponse getReporteGanancias(LocalDate desde, LocalDate hasta);
    List<ReporteInventarioItem> getReporteInventario();
    List<ProductoMasVendidoResponse> getProductosMasVendidos(LocalDate desde, LocalDate hasta, int limite);
}
