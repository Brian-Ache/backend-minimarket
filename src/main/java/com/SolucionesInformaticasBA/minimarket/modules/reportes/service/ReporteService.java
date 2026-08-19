package com.SolucionesInformaticasBA.minimarket.modules.reportes.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.SolucionesInformaticasBA.minimarket.modules.compras.api.CompraApi;
import com.SolucionesInformaticasBA.minimarket.modules.compras.api.dto.CompraResponse;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.api.InventarioApi;
import com.SolucionesInformaticasBA.minimarket.modules.productos.api.ProductosApi;
import com.SolucionesInformaticasBA.minimarket.modules.productos.api.dto.ProductoResponse;
import com.SolucionesInformaticasBA.minimarket.modules.reportes.api.ReportesApi;
import com.SolucionesInformaticasBA.minimarket.modules.reportes.api.dto.ProductoMasVendidoResponse;
import com.SolucionesInformaticasBA.minimarket.modules.reportes.api.dto.ReporteGananciasResponse;
import com.SolucionesInformaticasBA.minimarket.modules.reportes.api.dto.ReporteGananciasResponse.GananciaDiaria;
import com.SolucionesInformaticasBA.minimarket.modules.reportes.api.dto.ReporteInventarioItem;
import com.SolucionesInformaticasBA.minimarket.modules.reportes.api.dto.ReporteVentasResponse;
import com.SolucionesInformaticasBA.minimarket.modules.reportes.api.dto.ReporteVentasResponse.VentaDiaria;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.api.VentasApi;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.api.dto.DetalleVentaResponse;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.api.dto.VentaResponse;

import lombok.AllArgsConstructor;

@Service
@AllArgsConstructor
public class ReporteService implements ReportesApi {
    private final VentasApi ventasApi;
    private final CompraApi compraApi;
    private final ProductosApi productosApi;
    private final InventarioApi inventarioApi;

    @Override
    public ReporteVentasResponse getReporteVentas(LocalDate desde, LocalDate hasta) {
        // Una sola consulta para todo el rango: antes se pedía el resumen día por día, así que
        // un reporte mensual disparaba 30 consultas.
        List<VentaResponse> ventas = ventasApi.getByFechaCobradas(
            desde.atStartOfDay(), hasta.plusDays(1).atStartOfDay());

        Map<LocalDate, float[]> porDiaMap = new HashMap<>();
        for (VentaResponse v : ventas) {
            LocalDate dia = fechaDeCobro(v);
            float[] acc = porDiaMap.computeIfAbsent(dia, k -> new float[2]);
            acc[0] += v.getTotal();
            acc[1] += 1;
        }

        // Los días sin ventas también salen en el reporte, en cero.
        List<VentaDiaria> porDia = new ArrayList<>();
        for (LocalDate d = desde; !d.isAfter(hasta); d = d.plusDays(1)) {
            float[] acc = porDiaMap.getOrDefault(d, new float[2]);
            porDia.add(VentaDiaria.builder()
                .fecha(d)
                .cantidad((int) acc[1])
                .total(acc[0])
                .build());
        }

        return ReporteVentasResponse.builder()
            .desde(desde)
            .hasta(hasta)
            .totalTransacciones(ventas.size())
            .totalIngresos((float) ventas.stream().mapToDouble(VentaResponse::getTotal).sum())
            .porDia(porDia)
            .build();
    }

    @Override
    public ReporteGananciasResponse getReporteGanancias(LocalDate desde, LocalDate hasta) {
        LocalDateTime desdeDt = desde.atStartOfDay();
        LocalDateTime hastaDt = hasta.plusDays(1).atStartOfDay();

        // Solo ventas cobradas: una venta abierta todavía no es plata ganada.
        List<VentaResponse> ventas = ventasApi.getByFechaCobradas(desdeDt, hastaDt);
        List<CompraResponse> compras = compraApi.getByFecha(desdeDt, hastaDt);

        float totalVentas = 0;
        float costoTotal = 0;
        int unidadesSinCosto = 0;
        Map<LocalDate, float[]> porDiaMap = new HashMap<>();

        for (VentaResponse v : ventas) {
            LocalDate dia = fechaDeCobro(v);
            float[] acc = porDiaMap.computeIfAbsent(dia, k -> new float[3]);

            for (DetalleVentaResponse d : v.getDetalles()) {
                float ventaLinea = d.getSubtotal();
                totalVentas += ventaLinea;
                acc[0] += ventaLinea;

                if (d.getCostoUnitario() != null) {
                    float costoLinea = d.getCostoUnitario() * d.getCantidad();
                    costoTotal += costoLinea;
                    acc[1] += costoLinea;
                } else {
                    // Ítem manual o producto sin costo cargado: se cuenta aparte para que
                    // quede claro que la ganancia informada está sobrestimada.
                    unidadesSinCosto += d.getCantidad();
                }
            }
        }

        float totalCompras = 0;
        for (CompraResponse c : compras) {
            totalCompras += c.getTotal();
            porDiaMap.computeIfAbsent(c.getFecha().toLocalDate(), k -> new float[3])[2] += c.getTotal();
        }

        List<GananciaDiaria> porDia = porDiaMap.entrySet().stream()
            .map(e -> GananciaDiaria.builder()
                .fecha(e.getKey())
                .ventas(e.getValue()[0])
                .costo(e.getValue()[1])
                .ganancia(e.getValue()[0] - e.getValue()[1])
                .compras(e.getValue()[2])
                .build())
            .sorted(Comparator.comparing(GananciaDiaria::getFecha))
            .toList();

        return ReporteGananciasResponse.builder()
            .desde(desde)
            .hasta(hasta)
            .totalVentas(totalVentas)
            .costoMercaderiaVendida(costoTotal)
            .gananciaBruta(totalVentas - costoTotal)
            .totalCompras(totalCompras)
            .unidadesSinCosto(unidadesSinCosto)
            .porDia(porDia)
            .build();
    }

    @Override
    public List<ReporteInventarioItem> getReporteInventario() {
        List<ProductoResponse> productos = productosApi.getAll();

        // Dos consultas agregadas en total: la tabla stock para los productos comunes y la
        // suma de lotes para los que manejan lotes, que antes salían siempre en 0.
        Map<UUID, Integer> existencias = inventarioApi.getExistenciasPorProducto();

        return productos.stream().map(p -> ReporteInventarioItem.builder()
                .idProducto(p.getId())
                .nombre(p.getNombre())
                .barcode(p.getBarcode())
                .stockActual(existencias.getOrDefault(p.getId(), 0))
                .precio(p.getPrecio())
                .costo(p.getCosto())
                .categoria(p.getCategoria())
                .manejaLotes(p.isManejaLotes())
                .build())
            .toList();
    }

    @Override
    public List<ProductoMasVendidoResponse> getProductosMasVendidos(LocalDate desde, LocalDate hasta, int limite) {
        LocalDateTime desdeDt = desde.atStartOfDay();
        LocalDateTime hastaDt = hasta.plusDays(1).atStartOfDay();

        // Misma fuente que el resto de los reportes de dinero: solo ventas cobradas.
        List<VentaResponse> ventas = ventasApi.getByFechaCobradas(desdeDt, hastaDt);

        Map<UUID, ProductoAgg> agg = new HashMap<>();

        for (VentaResponse venta : ventas) {
            for (DetalleVentaResponse d : venta.getDetalles()) {
                if (d.getIdProducto() == null) continue;

                ProductoAgg item = agg.computeIfAbsent(d.getIdProducto(), k -> new ProductoAgg());
                item.nombre = d.getNombre();
                item.cantidad += d.getCantidad();
                item.total += d.getSubtotal();
            }
        }

        return agg.entrySet().stream()
            .sorted(Map.Entry.<UUID, ProductoAgg>comparingByValue(
                Comparator.comparingInt((ProductoAgg a) -> a.cantidad).reversed()))
            .limit(limite)
            .map(e -> ProductoMasVendidoResponse.builder()
                .idProducto(e.getKey())
                .nombre(e.getValue().nombre)
                .cantidadVendida(e.getValue().cantidad)
                .totalVendido(e.getValue().total)
                .build())
            .toList();
    }

    /** Día al que imputar la venta: el del cobro, que es cuando entró la plata. */
    private LocalDate fechaDeCobro(VentaResponse v) {
        return v.getFechaCobro() != null ? v.getFechaCobro().toLocalDate() : v.getFecha().toLocalDate();
    }

    private static class ProductoAgg {
        String nombre;
        int cantidad;
        float total;
    }
}
