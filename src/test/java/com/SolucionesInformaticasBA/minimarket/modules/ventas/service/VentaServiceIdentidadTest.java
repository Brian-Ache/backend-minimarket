package com.SolucionesInformaticasBA.minimarket.modules.ventas.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import com.SolucionesInformaticasBA.minimarket.modules.ventas.entity.DetalleVenta;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.entity.Venta;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.enums.OrigenVenta;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.repository.DetalleVentaRepository;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.repository.VentaRepository;
import com.SolucionesInformaticasBA.minimarket.shared.Uuid7;

/**
 * La venta online después de A1: el id y la fecha dejaron de ponerlos Hibernate.
 *
 * <p>Antes el id lo generaba {@code @GeneratedValue} —un v4— y la fecha
 * {@code @CreationTimestamp}. Las dos anotaciones se fueron para que el ticket offline pueda
 * traer su propio id y su propia fecha, y el riesgo de ese cambio es justamente que el camino
 * online se quede sin ninguno de los dos y la fila muera contra un NOT NULL.
 */
@ExtendWith(MockitoExtension.class)
class VentaServiceIdentidadTest {

    @Mock private VentaRepository ventaRepository;
    @Mock private DetalleVentaRepository detalleVentaRepository;
    @Mock private UsuarioApi usuarioApi;
    @Mock private ProductosApi productosApi;
    @Mock private InventarioApi inventarioApi;
    @Mock private LoteRepository loteRepository;
    @Mock private MovimientoStockRepository movimientoStockRepository;
    @Mock private CajaApi cajaApi;
    @Mock private DescontadorStock descontadorStock;

    @InjectMocks private VentaService ventaService;

    private static final UUID ID_USUARIO = UUID.randomUUID();
    private static final UUID ID_PRODUCTO = UUID.fromString("11111111-1111-4111-8111-111111111111");

    @Test
    @DisplayName("la venta online nace con un uuid v7 generado por el backend")
    void laVentaNaceConUuidV7() {
        prepararVenta();
        when(productosApi.getById(ID_PRODUCTO)).thenReturn(producto(1000f));

        VentaResponse response = ventaService.realizarVenta(ID_USUARIO, ventaCon(linea(2)));

        assertNotNull(response.getId());
        assertTrue(Uuid7.esV7(response.getId()),
                "el id tiene que ser v7: un v4 como PK fragmenta el indice de InnoDB");
    }

    @Test
    @DisplayName("la fecha la pone el servidor, y es la misma en la cabecera y en sus líneas")
    void laFechaLaPoneElServidor() {
        prepararVenta();
        when(productosApi.getById(ID_PRODUCTO)).thenReturn(producto(1000f));

        LocalDateTime antes = LocalDateTime.now();
        ventaService.realizarVenta(ID_USUARIO, ventaCon(linea(1)));
        LocalDateTime despues = LocalDateTime.now();

        ArgumentCaptor<Venta> capturaVenta = ArgumentCaptor.forClass(Venta.class);
        verify(ventaRepository).save(capturaVenta.capture());
        LocalDateTime fechaVenta = capturaVenta.getValue().getCreatedAt();

        assertNotNull(fechaVenta, "sin @CreationTimestamp, si nadie la pone la fila no entra");
        assertTrue(!fechaVenta.isBefore(antes) && !fechaVenta.isAfter(despues));

        // La línea lleva la misma fecha que su cabecera. En un ticket offline las dos van a ser
        // la del front, así que tienen que salir del mismo lugar también acá.
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<DetalleVenta>> capturaDetalles = ArgumentCaptor.forClass(List.class);
        verify(detalleVentaRepository).saveAll(capturaDetalles.capture());
        for (DetalleVenta d : capturaDetalles.getValue()) {
            assertEquals(fechaVenta, d.getCreatedAt());
            assertTrue(Uuid7.esV7(d.getId()), "el detalle también lleva v7");
        }
    }

    @Test
    @DisplayName("la venta online queda marcada como ONLINE y sin rastros de sincronización")
    void laVentaOnlineNoTieneRastrosDeSync() {
        prepararVenta();
        when(productosApi.getById(ID_PRODUCTO)).thenReturn(producto(1000f));

        ventaService.realizarVenta(ID_USUARIO, ventaCon(linea(1)));

        ArgumentCaptor<Venta> captura = ArgumentCaptor.forClass(Venta.class);
        verify(ventaRepository).save(captura.capture());
        Venta venta = captura.getValue();

        assertEquals(OrigenVenta.ONLINE, venta.getOrigen());
        assertEquals(null, venta.getSincronizadoEn(), "una venta online llega en el momento");
        assertEquals(null, venta.getIdUsuarioSync());
        assertEquals(false, venta.isRequiereRevision());
    }

    @Test
    @DisplayName("el total en BigDecimal no arrastra el redondeo que arrastraba el float")
    void elTotalNoArrastraRedondeo() {
        prepararVenta();
        // 0.10 no existe en binario. Sumado diez veces en float da 1.0000001, y ese era
        // exactamente el centavo que aparecía de la nada en los totales.
        when(productosApi.getById(ID_PRODUCTO)).thenReturn(producto(0.10f));

        VentaResponse response = ventaService.realizarVenta(ID_USUARIO, ventaCon(linea(10)));

        assertEquals(new BigDecimal("1.00"), response.getTotal());

        // Y la demostración de que el problema era real, no teórico.
        float enFloat = 0f;
        for (int i = 0; i < 10; i++) {
            enFloat += 0.10f;
        }
        assertTrue(enFloat != 1.0f, "si esto falla, el float dejó de ser el problema que era");
    }

    @Test
    @DisplayName("el total de un ticket largo es exacto: es lo que el front tiene que poder igualar")
    void elTotalDeUnTicketLargoEsExacto() {
        prepararVenta();
        when(productosApi.getById(ID_PRODUCTO)).thenReturn(producto(1200.05f));

        // Veinte líneas de 1200.05: 24001.00 exacto. En float la suma se corre.
        DetalleVentaRequest[] lineas = new DetalleVentaRequest[20];
        for (int i = 0; i < lineas.length; i++) {
            lineas[i] = linea(1);
        }

        VentaResponse response = ventaService.realizarVenta(ID_USUARIO, ventaCon(lineas));

        assertEquals(new BigDecimal("24001.00"), response.getTotal());
    }

    @Test
    @DisplayName("una venta recién armada se declara nueva, y deja de serlo al persistirse")
    void laVentaSeDeclaraNueva() {
        Venta venta = Venta.builder().id(Uuid7.nuevo()).idUsuario(ID_USUARIO).build();

        // Spring Data decide entre persist() y merge() con esto. Como el id lo ponemos
        // nosotros, sin el flag toda venta le parecería ya existente y cada INSERT vendría
        // precedido de un SELECT que no devuelve nada.
        assertTrue(venta.isNew());

        // Lo que hacen @PrePersist y @PostLoad cuando la fila entra o se lee.
        venta.setNuevo(false);
        assertTrue(!venta.isNew());

        DetalleVenta detalle = DetalleVenta.builder().id(Uuid7.nuevo()).build();
        assertTrue(detalle.isNew());
        detalle.setNuevo(false);
        assertTrue(!detalle.isNew());
    }

    private void prepararVenta() {
        when(usuarioApi.existById(ID_USUARIO)).thenReturn(true);
        when(ventaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(ventaRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private ProductoResponse producto(float precio) {
        return ProductoResponse.builder()
                .id(ID_PRODUCTO)
                .nombre("Yerba 1kg")
                .precio(precio)
                .manejaLotes(false)
                .build();
    }

    private DetalleVentaRequest linea(int cantidad) {
        DetalleVentaRequest d = new DetalleVentaRequest();
        d.setTipo("PRODUCTO");
        d.setIdProducto(ID_PRODUCTO);
        d.setCantidad(cantidad);
        return d;
    }

    private VentaRequest ventaCon(DetalleVentaRequest... lineas) {
        VentaRequest request = new VentaRequest();
        request.setDetalles(List.of(lineas));
        return request;
    }
}
