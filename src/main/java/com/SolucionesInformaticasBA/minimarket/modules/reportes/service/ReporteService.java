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
        List<VentaDiaria> porDia = new ArrayList<>();
        int totalTransacciones = 0;
        float totalIngresos = 0;

        LocalDate fecha = desde;
        while (!fecha.isAfter(hasta)) {
            com.SolucionesInformaticasBA.minimarket.modules.ventas.api.dto.ResumenDiarioResponse resumen =
                ventasApi.getResumenDiario(fecha);

            porDia.add(VentaDiaria.builder()
                .fecha(fecha)
                .cantidad(resumen.getCantidadVentas())
                .total(resumen.getTotalVentas())
                .build());

            totalTransacciones += resumen.getCantidadVentas();
            totalIngresos += resumen.getTotalVentas();
            fecha = fecha.plusDays(1);
        }

        return ReporteVentasResponse.builder()
            .desde(desde)
            .hasta(hasta)
            .totalTransacciones(totalTransacciones)
            .totalIngresos(totalIngresos)
            .porDia(porDia)
            .build();
    }

    @Override
    public ReporteGananciasResponse getReporteGanancias(LocalDate desde, LocalDate hasta) {
        LocalDateTime desdeDt = desde.atStartOfDay();
        LocalDateTime hastaDt = hasta.plusDays(1).atStartOfDay();

        List<VentaResponse> ventas = ventasApi.getByFecha(desdeDt, hastaDt);
        List<CompraResponse> compras = compraApi.getByFecha(desdeDt, hastaDt);

        float totalVentas = (float) ventas.stream().mapToDouble(VentaResponse::getTotal).sum();
        float totalCompras = (float) compras.stream().mapToDouble(CompraResponse::getTotal).sum();

        Map<LocalDate, float[]> porDiaMap = new HashMap<>();
        for (VentaResponse v : ventas) {
            LocalDate d = v.getFecha().toLocalDate();
            porDiaMap.computeIfAbsent(d, k -> new float[2])[0] += v.getTotal();
        }
        for (CompraResponse c : compras) {
            LocalDate d = c.getFecha().toLocalDate();
            porDiaMap.computeIfAbsent(d, k -> new float[2])[1] += c.getTotal();
        }

        List<GananciaDiaria> porDia = porDiaMap.entrySet().stream()
            .map(e -> GananciaDiaria.builder()
                .fecha(e.getKey())
                .ventas(e.getValue()[0])
                .compras(e.getValue()[1])
                .ganancia(e.getValue()[0] - e.getValue()[1])
                .build())
            .sorted(Comparator.comparing(GananciaDiaria::getFecha))
            .toList();

        return ReporteGananciasResponse.builder()
            .desde(desde)
            .hasta(hasta)
            .totalVentas(totalVentas)
            .totalCompras(totalCompras)
            .gananciaBruta(totalVentas - totalCompras)
            .porDia(porDia)
            .build();
    }

    @Override
    public List<ReporteInventarioItem> getReporteInventario() {
        List<ProductoResponse> productos = productosApi.getAll();

        return productos.stream().map(p -> {
            int stock = 0;
            try {
                stock = inventarioApi.getByIdProducto(p.getId()).getCantidad();
            } catch (Exception e) {
                stock = 0;
            }

            return ReporteInventarioItem.builder()
                .idProducto(p.getId())
                .nombre(p.getNombre())
                .barcode(p.getBarcode())
                .stockActual(stock)
                .precio(p.getPrecio())
                .costo(p.getCosto())
                .categoria(p.getCategoria())
                .manejaLotes(p.isManejaLotes())
                .build();
        }).toList();
    }

    @Override
    public List<ProductoMasVendidoResponse> getProductosMasVendidos(LocalDate desde, LocalDate hasta, int limite) {
        LocalDateTime desdeDt = desde.atStartOfDay();
        LocalDateTime hastaDt = hasta.plusDays(1).atStartOfDay();

        List<VentaResponse> ventas = ventasApi.getByFecha(desdeDt, hastaDt);

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

    private static class ProductoAgg {
        String nombre;
        int cantidad;
        float total;
    }
}
