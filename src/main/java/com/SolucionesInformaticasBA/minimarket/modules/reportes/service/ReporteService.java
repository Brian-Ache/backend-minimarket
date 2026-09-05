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

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.SolucionesInformaticasBA.minimarket.modules.compras.api.CompraApi;
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
import com.SolucionesInformaticasBA.minimarket.shared.exeption.BadRequestException;

import lombok.AllArgsConstructor;

@Service
@AllArgsConstructor
public class ReporteService implements ReportesApi {
    /**
     * Un reporte devuelve una fila por día del rango y carga en memoria todas las ventas del
     * período con sus detalles, así que la amplitud tiene que estar acotada: sin tope,
     * {@code desde=0001-01-01&hasta=9999-12-31} construía más de tres millones de filas y se
     * comía el heap del servidor. Un año cubre el caso más largo que se pide de verdad
     * —comparar contra el mismo mes del año pasado—; 366 y no 365 para que un año bisiesto
     * completo entre justo.
     */
    private static final int MAX_DIAS_RANGO = 366;

    private final VentasApi ventasApi;
    private final CompraApi compraApi;
    private final ProductosApi productosApi;
    private final InventarioApi inventarioApi;

    @Override
    public ReporteVentasResponse getReporteVentas(LocalDate desde, LocalDate hasta) {
        validarRango(desde, hasta);

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
        validarRango(desde, hasta);

        LocalDateTime desdeDt = desde.atStartOfDay();
        LocalDateTime hastaDt = hasta.plusDays(1).atStartOfDay();

        // Solo ventas cobradas: una venta abierta todavía no es plata ganada.
        List<VentaResponse> ventas = ventasApi.getByFechaCobradas(desdeDt, hastaDt);
        // Totales por día y no el listado de compras: getAllFiltered arma la respuesta completa
        // de cada compra —todos sus detalles y una consulta de proveedor por fila— para que acá
        // se usen nada más que la fecha y el importe.
        Map<LocalDate, Float> comprasPorDia = compraApi.getTotalesPorDia(desdeDt, hastaDt);

        float totalVentas = 0;
        float costoTotal = 0;
        int unidadesSinCosto = 0;
        Map<LocalDate, float[]> porDiaMap = new HashMap<>();

        for (VentaResponse v : ventas) {
            LocalDate dia = fechaDeCobro(v);
            float[] acc = porDiaMap.computeIfAbsent(dia, k -> new float[2]);

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
        for (float total : comprasPorDia.values()) {
            totalCompras += total;
        }

        // Igual que el reporte de ventas: el rango sale completo, con los días sin movimiento
        // en cero. Devolviendo solo los días con datos, dos reportes del mismo período tenían
        // arrays de distinto largo y no se podían graficar juntos sin rellenarlos en el front.
        List<GananciaDiaria> porDia = new ArrayList<>();
        for (LocalDate d = desde; !d.isAfter(hasta); d = d.plusDays(1)) {
            float[] acc = porDiaMap.getOrDefault(d, new float[2]);
            porDia.add(GananciaDiaria.builder()
                .fecha(d)
                .ventas(acc[0])
                .costo(acc[1])
                .ganancia(acc[0] - acc[1])
                .compras(comprasPorDia.getOrDefault(d, 0f))
                .build());
        }

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
        List<ProductoResponse> productos = productosApi.getAll(PageRequest.of(0, Integer.MAX_VALUE)).getContent();

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
        validarRango(desde, hasta);

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

        // Desempate por importe y después por id: ordenando solo por cantidad, dos productos
        // empatados quedaban en el orden en que los devolviera el HashMap, así que el corte del
        // limite podía dejar afuera a uno u otro sin criterio y cambiar entre dos llamadas
        // iguales.
        List<Map.Entry<UUID, ProductoAgg>> ordenados = new ArrayList<>(agg.entrySet());
        ordenados.sort(Comparator
            .<Map.Entry<UUID, ProductoAgg>>comparingInt(e -> e.getValue().cantidad).reversed()
            .thenComparing(e -> e.getValue().total, Comparator.reverseOrder())
            .thenComparing(Map.Entry::getKey));

        List<Map.Entry<UUID, ProductoAgg>> top = ordenados.stream().limit(limite).toList();

        // Los barcodes se piden recién sobre el top ya recortado, en una sola consulta: no
        // están en el detalle de la venta —que congela nombre y precio, no el código— y sin
        // esto el campo salía siempre en null, aunque el contrato lo documenta.
        Map<UUID, String> barcodes = productosApi.getBarcodesPorId(
            top.stream().map(Map.Entry::getKey).toList());

        return top.stream()
            .map(e -> ProductoMasVendidoResponse.builder()
                .idProducto(e.getKey())
                .nombre(e.getValue().nombre)
                .barcode(barcodes.get(e.getKey()))
                .cantidadVendida(e.getValue().cantidad)
                .totalVendido(e.getValue().total)
                .build())
            .toList();
    }

    /**
     * El rango es inclusivo de las dos puntas y está acotado en amplitud. Invertido no es un
     * reporte vacío sino un error de quien pregunta, así que va 400 y no una respuesta en cero
     * que se lee como "no hubo ventas".
     */
    private void validarRango(LocalDate desde, LocalDate hasta) {
        if (desde.isAfter(hasta)) {
            throw new BadRequestException(
                "El rango de fechas es inválido: 'desde' no puede ser posterior a 'hasta'");
        }

        long dias = ChronoUnit.DAYS.between(desde, hasta) + 1;
        if (dias > MAX_DIAS_RANGO) {
            throw new BadRequestException("El rango no puede superar los " + MAX_DIAS_RANGO
                + " días y se pidieron " + dias + ". Acotá el período o pedilo por partes");
        }
    }

    /**
     * Día al que imputar la venta: el del cobro, que es cuando entró la plata. La consulta que
     * las trae exige fechaCobro, así que acá nunca es null.
     */
    private LocalDate fechaDeCobro(VentaResponse v) {
        return v.getFechaCobro().toLocalDate();
    }

    private static class ProductoAgg {
        String nombre;
        int cantidad;
        float total;
    }
}
