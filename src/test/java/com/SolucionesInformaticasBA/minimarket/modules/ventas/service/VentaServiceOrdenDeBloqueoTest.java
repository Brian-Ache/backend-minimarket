package com.SolucionesInformaticasBA.minimarket.modules.ventas.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.SolucionesInformaticasBA.minimarket.modules.caja.api.CajaApi;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.api.InventarioApi;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.repository.LoteRepository;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.repository.MovimientoStockRepository;
import com.SolucionesInformaticasBA.minimarket.modules.productos.api.ProductosApi;
import com.SolucionesInformaticasBA.minimarket.modules.productos.api.dto.ProductoResponse;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.api.UsuarioApi;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.api.dto.DetalleVentaRequest;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.api.dto.VentaRequest;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.api.dto.VentaResponse;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.entity.Venta;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.repository.DetalleVentaRepository;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.repository.VentaRepository;

/**
 * El inventario se bloquea siempre por idProducto ascendente. Dos ventas de los mismos
 * productos cargados en orden distinto se quedaban cada una con la fila que la otra necesitaba
 * y la base mataba una por deadlock; con un orden único eso no puede pasar. El ticket, en
 * cambio, se guarda y se devuelve como lo cargó el cajero.
 */
@ExtendWith(MockitoExtension.class)
class VentaServiceOrdenDeBloqueoTest {

    @Mock private VentaRepository ventaRepository;
    @Mock private DetalleVentaRepository detalleVentaRepository;
    @Mock private UsuarioApi usuarioApi;
    @Mock private ProductosApi productosApi;
    @Mock private InventarioApi inventarioApi;
    @Mock private LoteRepository loteRepository;
    @Mock private MovimientoStockRepository movimientoStockRepository;
    @Mock private CajaApi cajaApi;

    @InjectMocks private VentaService ventaService;

    private static final UUID ID_USUARIO = UUID.randomUUID();
    // El orden es el de UUID.compareTo, que compara con signo: un UUID que empieza con 9
    // ordena ANTES que uno que empieza con 1. Cuál va primero da igual mientras sea el mismo
    // criterio en todas partes; estos dos se eligen para que el test no dependa de esa sutileza.
    private static final UUID ID_PRIMERO = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID ID_SEGUNDO = UUID.fromString("22222222-2222-4222-8222-222222222222");

    @Test
    @DisplayName("los productos se tocan por id ascendente, aunque el ticket venga al revés")
    void bloqueaEnOrdenDeIdProducto() {
        assertTrue(ID_PRIMERO.compareTo(ID_SEGUNDO) < 0, "el fixture asume este orden");
        prepararVenta();
        when(productosApi.getById(ID_PRIMERO)).thenReturn(producto(ID_PRIMERO, "Agua", 1000f));
        when(productosApi.getById(ID_SEGUNDO)).thenReturn(producto(ID_SEGUNDO, "Pan", 2000f));

        // El cajero carga primero el que va segundo en el orden de bloqueo.
        ventaService.realizarVenta(ID_USUARIO, ventaCon(
                linea(ID_SEGUNDO, 1),
                linea(ID_PRIMERO, 1)));

        InOrder orden = inOrder(productosApi);
        orden.verify(productosApi).getById(ID_PRIMERO);
        orden.verify(productosApi).getById(ID_SEGUNDO);
    }

    @Test
    @DisplayName("el detalle se devuelve en el orden en que se cargó, no en el de bloqueo")
    void elTicketConservaSuOrden() {
        prepararVenta();
        when(productosApi.getById(ID_PRIMERO)).thenReturn(producto(ID_PRIMERO, "Agua", 1000f));
        when(productosApi.getById(ID_SEGUNDO)).thenReturn(producto(ID_SEGUNDO, "Pan", 2000f));

        VentaResponse response = ventaService.realizarVenta(ID_USUARIO, ventaCon(
                linea(ID_SEGUNDO, 1),
                linea(ID_PRIMERO, 1)));

        assertEquals(List.of("Pan", "Agua"),
                response.getDetalles().stream().map(d -> d.getNombre()).toList());
    }

    @Test
    @DisplayName("los ítems manuales van al final del orden de bloqueo y no rompen nada")
    void losManualesNoAlteranElOrden() {
        prepararVenta();
        when(productosApi.getById(ID_PRIMERO)).thenReturn(producto(ID_PRIMERO, "Agua", 1000f));

        DetalleVentaRequest manual = new DetalleVentaRequest();
        manual.setTipo("MANUAL");
        manual.setCantidad(1);
        manual.setNombreManual("Bolsa");
        manual.setPrecioUnitario(500f);

        VentaResponse response = ventaService.realizarVenta(ID_USUARIO, ventaCon(manual, linea(ID_PRIMERO, 1)));

        assertEquals(List.of("Bolsa", "Agua"),
                response.getDetalles().stream().map(d -> d.getNombre()).toList());
        assertEquals(1500f, response.getTotal());
    }

    private void prepararVenta() {
        when(usuarioApi.existById(ID_USUARIO)).thenReturn(true);
        when(ventaRepository.save(any())).thenAnswer(inv -> {
            Venta v = inv.getArgument(0);
            if (v.getId() == null) v.setId(UUID.randomUUID());
            return v;
        });
        when(ventaRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private ProductoResponse producto(UUID id, String nombre, float precio) {
        return ProductoResponse.builder()
                .id(id)
                .nombre(nombre)
                .precio(precio)
                .manejaLotes(false)
                .build();
    }

    private DetalleVentaRequest linea(UUID idProducto, int cantidad) {
        DetalleVentaRequest d = new DetalleVentaRequest();
        d.setTipo("PRODUCTO");
        d.setIdProducto(idProducto);
        d.setCantidad(cantidad);
        return d;
    }

    private VentaRequest ventaCon(DetalleVentaRequest... lineas) {
        VentaRequest request = new VentaRequest();
        request.setDetalles(List.of(lineas));
        return request;
    }
}
