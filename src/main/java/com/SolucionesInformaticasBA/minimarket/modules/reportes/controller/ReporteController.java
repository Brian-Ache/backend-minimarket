package com.SolucionesInformaticasBA.minimarket.modules.reportes.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.SolucionesInformaticasBA.minimarket.modules.reportes.api.ReportesApi;
import com.SolucionesInformaticasBA.minimarket.modules.reportes.api.dto.ProductoMasVendidoResponse;
import com.SolucionesInformaticasBA.minimarket.modules.reportes.api.dto.ReporteGananciasResponse;
import com.SolucionesInformaticasBA.minimarket.modules.reportes.api.dto.ReporteInventarioItem;
import com.SolucionesInformaticasBA.minimarket.modules.reportes.api.dto.ReporteVentasResponse;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;

@RestController
@RequestMapping("/api/reportes")
@AllArgsConstructor
public class ReporteController {

    /** Techo del top de productos, mismo criterio que el tamaño de página del resto. */
    private static final int MAX_LIMITE = 100;

    private final ReportesApi reportesApi;

    @GetMapping("/v1/ventas")
    public ResponseEntity<ReporteVentasResponse> ventas(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return ResponseEntity.ok(reportesApi.getReporteVentas(desde, hasta));
    }

    @GetMapping("/v1/ganancias")
    public ResponseEntity<ReporteGananciasResponse> ganancias(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return ResponseEntity.ok(reportesApi.getReporteGanancias(desde, hasta));
    }

    @GetMapping("/v1/inventario")
    public ResponseEntity<List<ReporteInventarioItem>> inventario() {
        return ResponseEntity.ok(reportesApi.getReporteInventario());
    }

    @GetMapping("/v1/productos-mas-vendidos")
    public ResponseEntity<List<ProductoMasVendidoResponse>> productosMasVendidos(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(defaultValue = "10")
                @Min(value = 1, message = "El límite debe ser al menos 1")
                @Max(value = MAX_LIMITE, message = "El límite no puede superar " + MAX_LIMITE)
                int limite) {
        return ResponseEntity.ok(reportesApi.getProductosMasVendidos(desde, hasta, limite));
    }
}
