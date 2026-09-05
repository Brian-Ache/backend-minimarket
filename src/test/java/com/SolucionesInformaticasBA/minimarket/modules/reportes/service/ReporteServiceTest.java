package com.SolucionesInformaticasBA.minimarket.modules.reportes.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import com.SolucionesInformaticasBA.minimarket.modules.compras.api.CompraApi;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.api.InventarioApi;
import com.SolucionesInformaticasBA.minimarket.modules.productos.api.ProductosApi;
import com.SolucionesInformaticasBA.minimarket.modules.productos.api.dto.ProductoResponse;
import com.SolucionesInformaticasBA.minimarket.modules.reportes.api.dto.ProductoMasVendidoResponse;
import com.SolucionesInformaticasBA.minimarket.modules.reportes.api.dto.ReporteGananciasResponse;
import com.SolucionesInformaticasBA.minimarket.modules.reportes.api.dto.ReporteInventarioItem;
import com.SolucionesInformaticasBA.minimarket.modules.reportes.api.dto.ReporteVentasResponse;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.api.VentasApi;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.api.dto.DetalleVentaResponse;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.api.dto.VentaResponse;
import com.SolucionesInformaticasBA.minimarket.shared.exeption.BadRequestException;

@ExtendWith(MockitoExtension.class)
class ReporteServiceTest {

    @Mock
    private VentasApi ventasApi;
    @Mock
    private CompraApi compraApi;
    @Mock
    private ProductosApi productosApi;
    @Mock
    private InventarioApi inventarioApi;

    @InjectMocks
    private ReporteService reporteService;

    private static final LocalDate DESDE = LocalDate.of(2026, 9, 1);
    private static final LocalDate HASTA = LocalDate.of(2026, 9, 3);

    // --- Validación del rango ---

    @Test
    @DisplayName("un rango invertido es 400 y no una respuesta en cero que se lee como 'no hubo ventas'")
    void rangoInvertidoEsBadRequest() {
        LocalDate desde = LocalDate.of(2026, 9, 10);
        LocalDate hasta = LocalDate.of(2026, 9, 1);

        assertThrows(BadRequestException.class, () -> reporteService.getReporteVentas(desde, hasta));
        assertThrows(BadRequestException.class, () -> reporteService.getReporteGanancias(desde, hasta));
        assertThrows(BadRequestException.class,
                () -> reporteService.getProductosMasVendidos(desde, hasta, 10));

        verifyNoInteractions(ventasApi, compraApi);
    }

    @Test
    @DisplayName("un rango de más de 366 días es 400: sin tope se armaban millones de filas en memoria")
    void rangoDemasiadoAmplioEsBadRequest() {
        LocalDate desde = LocalDate.of(1, 1, 1);
        LocalDate hasta = LocalDate.of(9999, 12, 31);

        assertThrows(BadRequestException.class, () -> reporteService.getReporteVentas(desde, hasta));
        assertThrows(BadRequestException.class, () -> reporteService.getReporteGanancias(desde, hasta));
        assertThrows(BadRequestException.class,
                () -> reporteService.getProductosMasVendidos(desde, hasta, 10));

        verifyNoInteractions(ventasApi, compraApi);
    }

    @Test
    @DisplayName("un año bisiesto completo entra justo en el tope")
    void unAnioCompletoEsValido() {
        LocalDate desde = LocalDate.of(2024, 1, 1);
        LocalDate hasta = LocalDate.of(2024, 12, 31);
        when(ventasApi.getByFechaCobradas(any(), any())).thenReturn(List.of());

        ReporteVentasResponse reporte = reporteService.getReporteVentas(desde, hasta);

        assertEquals(366, reporte.getPorDia().size());
    }

    // --- Reporte de ventas ---

    @Test
    @DisplayName("las ventas se imputan al día del cobro y los días sin ventas salen en cero")
    void reporteDeVentasPorDiaDeCobro() {
        when(ventasApi.getByFechaCobradas(DESDE.atStartOfDay(), HASTA.plusDays(1).atStartOfDay()))
                .thenReturn(List.of(
                        venta(LocalDateTime.of(2026, 9, 1, 10, 0), 100f),
                        venta(LocalDateTime.of(2026, 9, 3, 18, 0), 50f)));

        ReporteVentasResponse reporte = reporteService.getReporteVentas(DESDE, HASTA);

        assertEquals(2, reporte.getTotalTransacciones());
        assertEquals(150f, reporte.getTotalIngresos());
        assertEquals(3, reporte.getPorDia().size());
        assertEquals(100f, reporte.getPorDia().get(0).getTotal());
        assertEquals(0f, reporte.getPorDia().get(1).getTotal());
        assertEquals(0, reporte.getPorDia().get(1).getCantidad());
        assertEquals(50f, reporte.getPorDia().get(2).getTotal());
    }

    // --- Reporte de ganancias ---

    @Test
    @DisplayName("el detalle diario cubre el rango completo, con los días sin movimiento en cero")
    void gananciasRellenaLosDiasSinMovimiento() {
        when(ventasApi.getByFechaCobradas(any(), any())).thenReturn(List.of(
                venta(LocalDateTime.of(2026, 9, 1, 10, 0), 100f,
                        detalle(UUID.randomUUID(), "Fideos", 2, 50f, 30f))));
        when(compraApi.getTotalesPorDia(any(), any()))
                .thenReturn(Map.of(LocalDate.of(2026, 9, 3), 500f));

        ReporteGananciasResponse reporte = reporteService.getReporteGanancias(DESDE, HASTA);

        assertEquals(3, reporte.getPorDia().size());
        assertEquals(100f, reporte.getPorDia().get(0).getVentas());
        assertEquals(60f, reporte.getPorDia().get(0).getCosto());
        assertEquals(40f, reporte.getPorDia().get(0).getGanancia());
        assertEquals(0f, reporte.getPorDia().get(1).getVentas());
        assertEquals(500f, reporte.getPorDia().get(2).getCompras());

        assertEquals(100f, reporte.getTotalVentas());
        assertEquals(60f, reporte.getCostoMercaderiaVendida());
        assertEquals(40f, reporte.getGananciaBruta());
        assertEquals(500f, reporte.getTotalCompras());
    }

    @Test
    @DisplayName("las líneas sin costo suman a las ventas pero se cuentan aparte")
    void gananciasCuentaLasUnidadesSinCosto() {
        when(ventasApi.getByFechaCobradas(any(), any())).thenReturn(List.of(
                venta(LocalDateTime.of(2026, 9, 2, 12, 0), 130f,
                        detalle(UUID.randomUUID(), "Yerba", 1, 100f, 70f),
                        detalle(null, "Changuito prestado", 3, 10f, null))));
        when(compraApi.getTotalesPorDia(any(), any())).thenReturn(Map.of());

        ReporteGananciasResponse reporte = reporteService.getReporteGanancias(DESDE, HASTA);

        assertEquals(130f, reporte.getTotalVentas());
        assertEquals(70f, reporte.getCostoMercaderiaVendida());
        assertEquals(3, reporte.getUnidadesSinCosto());
    }

    @Test
    @DisplayName("las compras se piden como totales por día, no como el listado completo")
    void gananciasNoPideElListadoDeCompras() {
        when(ventasApi.getByFechaCobradas(any(), any())).thenReturn(List.of());
        when(compraApi.getTotalesPorDia(any(), any())).thenReturn(Map.of());

        reporteService.getReporteGanancias(DESDE, HASTA);

        verify(compraApi).getTotalesPorDia(DESDE.atStartOfDay(), HASTA.plusDays(1).atStartOfDay());
        verify(compraApi, never()).getAllFiltered(any(), any(), any(), any(), any());
    }

    // --- Productos más vendidos ---

    @Test
    @DisplayName("el barcode se resuelve por id: el detalle de la venta no lo congela y salía en null")
    void topProductosTraeElBarcode() {
        UUID idProducto = UUID.randomUUID();
        when(ventasApi.getByFechaCobradas(any(), any())).thenReturn(List.of(
                venta(LocalDateTime.of(2026, 9, 1, 10, 0), 200f,
                        detalle(idProducto, "Fideos", 4, 50f, 30f))));
        when(productosApi.getBarcodesPorId(anyCollection()))
                .thenReturn(Map.of(idProducto, "7791234567890"));

        List<ProductoMasVendidoResponse> top = reporteService.getProductosMasVendidos(DESDE, HASTA, 10);

        assertEquals(1, top.size());
        assertEquals("7791234567890", top.get(0).getBarcode());
        assertEquals("Fideos", top.get(0).getNombre());
        assertEquals(4, top.get(0).getCantidadVendida());
        assertEquals(200f, top.get(0).getTotalVendido());
    }

    @Test
    @DisplayName("un producto sin barcode cargado no rompe el reporte")
    void topProductosToleraElBarcodeNulo() {
        UUID idProducto = UUID.randomUUID();
        when(ventasApi.getByFechaCobradas(any(), any())).thenReturn(List.of(
                venta(LocalDateTime.of(2026, 9, 1, 10, 0), 50f,
                        detalle(idProducto, "Suelto", 1, 50f, null))));
        when(productosApi.getBarcodesPorId(anyCollection())).thenReturn(Map.of());

        List<ProductoMasVendidoResponse> top = reporteService.getProductosMasVendidos(DESDE, HASTA, 10);

        assertNull(top.get(0).getBarcode());
    }

    @Test
    @DisplayName("los barcodes se piden solo para el top ya recortado")
    void topProductosNoPideBarcodesDeMas() {
        UUID masVendido = UUID.randomUUID();
        UUID menosVendido = UUID.randomUUID();
        when(ventasApi.getByFechaCobradas(any(), any())).thenReturn(List.of(
                venta(LocalDateTime.of(2026, 9, 1, 10, 0), 300f,
                        detalle(masVendido, "Fideos", 10, 20f, 10f),
                        detalle(menosVendido, "Arroz", 1, 100f, 60f))));
        when(productosApi.getBarcodesPorId(anyCollection())).thenReturn(Map.of());

        reporteService.getProductosMasVendidos(DESDE, HASTA, 1);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<UUID>> ids = ArgumentCaptor.forClass(Collection.class);
        verify(productosApi).getBarcodesPorId(ids.capture());
        assertEquals(List.of(masVendido), List.copyOf(ids.getValue()));
    }

    @Test
    @DisplayName("con la misma cantidad vendida desempata el importe, así el corte del top es estable")
    void topProductosDesempataPorImporte() {
        UUID caro = UUID.randomUUID();
        UUID barato = UUID.randomUUID();
        when(ventasApi.getByFechaCobradas(any(), any())).thenReturn(List.of(
                venta(LocalDateTime.of(2026, 9, 1, 10, 0), 300f,
                        detalle(barato, "Arroz", 5, 20f, 10f),
                        detalle(caro, "Aceite", 5, 40f, 25f))));
        when(productosApi.getBarcodesPorId(anyCollection())).thenReturn(Map.of());

        List<ProductoMasVendidoResponse> top = reporteService.getProductosMasVendidos(DESDE, HASTA, 2);

        assertEquals(caro, top.get(0).getIdProducto());
        assertEquals(barato, top.get(1).getIdProducto());
    }

    @Test
    @DisplayName("los ítems manuales no entran en el top: no son un producto del catálogo")
    void topProductosIgnoraLosManuales() {
        when(ventasApi.getByFechaCobradas(any(), any())).thenReturn(List.of(
                venta(LocalDateTime.of(2026, 9, 1, 10, 0), 30f,
                        detalle(null, "Bolsa", 3, 10f, null))));
        when(productosApi.getBarcodesPorId(anyCollection())).thenReturn(Map.of());

        assertEquals(List.of(), reporteService.getProductosMasVendidos(DESDE, HASTA, 10));
    }

    // --- Inventario ---

    @Test
    @DisplayName("un producto sin fila de existencias es stock 0, no un error")
    void inventarioResuelveLasExistenciasEnUnaConsulta() {
        UUID conStock = UUID.randomUUID();
        UUID sinStock = UUID.randomUUID();
        Page<ProductoResponse> pagina = new PageImpl<>(List.of(
                producto(conStock, "Fideos", "779"),
                producto(sinStock, "Arroz", null)), PageRequest.of(0, 20), 2);
        when(productosApi.getAll(any())).thenReturn(pagina);
        // Acotado a los productos de la página: pedir las existencias de todo el catálogo
        // para armar una página de 20 anulaba el trabajo de paginarla.
        when(inventarioApi.getExistenciasPorProductos(List.of(conStock, sinStock)))
                .thenReturn(Map.of(conStock, 7));

        List<ReporteInventarioItem> items =
                reporteService.getReporteInventario(PageRequest.of(0, 20)).getContent();

        verify(inventarioApi, never()).getExistenciasPorProducto();
        assertEquals(7, items.get(0).getStockActual());
        assertEquals("779", items.get(0).getBarcode());
        assertEquals(0, items.get(1).getStockActual());
    }

    // --- Helpers ---

    private VentaResponse venta(LocalDateTime fechaCobro, float total, DetalleVentaResponse... detalles) {
        VentaResponse venta = new VentaResponse();
        venta.setId(UUID.randomUUID());
        venta.setFecha(fechaCobro.minusMinutes(5));
        venta.setFechaCobro(fechaCobro);
        venta.setCobrada(true);
        venta.setTotal(total);
        venta.setDetalles(List.of(detalles));
        return venta;
    }

    private DetalleVentaResponse detalle(UUID idProducto, String nombre, int cantidad,
                                         float precioUnitario, Float costoUnitario) {
        DetalleVentaResponse detalle = new DetalleVentaResponse();
        detalle.setIdProducto(idProducto);
        detalle.setNombre(nombre);
        detalle.setCantidad(cantidad);
        detalle.setPrecioUnitario(precioUnitario);
        detalle.setSubtotal(precioUnitario * cantidad);
        detalle.setCostoUnitario(costoUnitario);
        detalle.setTipo(idProducto == null ? "MANUAL" : "PRODUCTO");
        return detalle;
    }

    private ProductoResponse producto(UUID id, String nombre, String barcode) {
        return ProductoResponse.builder()
                .id(id)
                .nombre(nombre)
                .barcode(barcode)
                .precio(100f)
                .build();
    }
}
