package com.SolucionesInformaticasBA.minimarket.modules.ventas.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.SolucionesInformaticasBA.minimarket.modules.caja.api.CajaApi;
import com.SolucionesInformaticasBA.minimarket.modules.caja.api.dto.SesionCajaResponse;
import com.SolucionesInformaticasBA.minimarket.modules.caja.enums.OrigenMovimientoCaja;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.api.InventarioApi;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.api.dto.MovimientoStockRequest;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.entity.Lote;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.entity.MovimientoStock;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.repository.LoteRepository;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.repository.MovimientoStockRepository;
import com.SolucionesInformaticasBA.minimarket.modules.productos.api.ProductosApi;
import com.SolucionesInformaticasBA.minimarket.modules.productos.api.dto.ProductoResponse;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.api.UsuarioApi;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.api.dto.DetalleSyncRequest;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.api.dto.EventoSyncRequest;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.enums.TipoEventoSync;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.repository.AnulacionPendienteRepository;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.repository.DetalleVentaRepository;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.repository.VentaRepository;
import com.SolucionesInformaticasBA.minimarket.shared.Uuid7;

/**
 * La fecha del ticket offline, propagada por toda la cadena.
 *
 * <p>Que la venta quede fechada cuando ocurrió no alcanza: cada ticket escribe además
 * movimientos de stock y, si fue en efectivo, un movimiento de caja. Si esos quedan fechados
 * cuando llegó el lote, <b>el kardex muestra la mercadería saliendo dos días después de la venta
 * que la sacó</b> y la plata entra al turno equivocado. Es el efecto colateral que se descubre
 * tarde, y estos tests existen para que no vuelva.
 */
@ExtendWith(MockitoExtension.class)
class FechaDelTicketOfflineTest {

    @Mock private VentaRepository ventaRepository;
    @Mock private DetalleVentaRepository detalleVentaRepository;
    @Mock private UsuarioApi usuarioApi;
    @Mock private ProductosApi productosApi;
    @Mock private CajaApi cajaApi;
    @Mock private AnuladorVentas anuladorVentas;
    @Mock private AnulacionPendienteRepository anulacionPendienteRepository;
    @Mock private InventarioApi inventarioApi;
    @Mock private LoteRepository loteRepository;
    @Mock private MovimientoStockRepository movimientoStockRepository;

    // El descontador va de verdad y no mockeado: lo que se está probando es justamente que la
    // fecha llegue hasta el movimiento que él escribe.
    private DescontadorStock descontadorStock;
    @InjectMocks private ProcesadorEventoSync procesador;

    private static final UUID ID_VENDEDOR = UUID.randomUUID();
    private static final UUID ID_SYNC = UUID.randomUUID();
    private static final UUID ID_SESION = UUID.randomUUID();
    private static final UUID ID_PRODUCTO = UUID.fromString("11111111-1111-4111-8111-111111111111");

    private final LocalDateTime anteayer = LocalDateTime.now().minusDays(2);

    @BeforeEach
    void armarProcesador() {
        descontadorStock = new DescontadorStock(inventarioApi, loteRepository, movimientoStockRepository);
        procesador = new ProcesadorEventoSync(ventaRepository, detalleVentaRepository, usuarioApi,
            productosApi, cajaApi, descontadorStock, anuladorVentas, anulacionPendienteRepository);
        ReflectionTestUtils.setField(procesador, "desfaseMaximoMinutos", 5L);
        ReflectionTestUtils.setField(procesador, "antiguedadMaximaDias", 60L);

        lenient().when(ventaRepository.findById(any())).thenReturn(java.util.Optional.empty());
        lenient().when(ventaRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(ventaRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(usuarioApi.existById(ID_VENDEDOR)).thenReturn(true);
        lenient().when(cajaApi.getSesionById(ID_SESION)).thenReturn(
            SesionCajaResponse.builder().id(ID_SESION).estado("ABIERTA").build());
        lenient().when(anulacionPendienteRepository.findById(any()))
            .thenReturn(java.util.Optional.empty());
    }

    @Test
    @DisplayName("el movimiento de stock de un producto por lotes lleva la fecha de la venta")
    void elMovimientoDeLotesSeFechaConLaVenta() {
        when(productosApi.getById(ID_PRODUCTO)).thenReturn(producto(true));
        when(loteRepository.findParaDescuentoFefo(ID_PRODUCTO)).thenReturn(List.of(
            Lote.builder().id(UUID.randomUUID()).idProducto(ID_PRODUCTO).cantidad(50)
                .createdAt(LocalDateTime.now().minusDays(30)).build()));

        procesador.procesar(ticket(), "caja-01", ID_SYNC);

        ArgumentCaptor<MovimientoStock> captor = ArgumentCaptor.forClass(MovimientoStock.class);
        verify(movimientoStockRepository).save(captor.capture());
        assertEquals(anteayer, captor.getValue().getCreatedAt(),
            "el kardex tiene que mostrar la mercadería saliendo cuando salió");
    }

    @Test
    @DisplayName("el descuento de un producto común viaja con la fecha en el request")
    void elDescuentoComunLlevaLaFecha() {
        when(productosApi.getById(ID_PRODUCTO)).thenReturn(producto(false));

        procesador.procesar(ticket(), "caja-01", ID_SYNC);

        ArgumentCaptor<MovimientoStockRequest> captor =
            ArgumentCaptor.forClass(MovimientoStockRequest.class);
        verify(inventarioApi).disminuirRegularizando(captor.capture());
        assertEquals(anteayer, captor.getValue().getFecha());
    }

    @Test
    @DisplayName("la plata entra a la caja con la fecha del ticket, no con la de llegada")
    void laCajaSeFechaConElTicket() {
        when(productosApi.getById(ID_PRODUCTO)).thenReturn(producto(false));

        procesador.procesar(ticket(), "caja-01", ID_SYNC);

        // Fechándola hoy, el turno viejo cerraría faltando plata y el de hoy sobrando.
        verify(cajaApi).registrarEntradaAutomatica(eq(ID_SESION), eq(ID_VENDEDOR), anyFloat(),
            eq(OrigenMovimientoCaja.VENTA), any(), eq(anteayer));
    }

    private ProductoResponse producto(boolean manejaLotes) {
        return ProductoResponse.builder()
            .id(ID_PRODUCTO).nombre("Yerba 1kg").precio(1200f).costo(800f)
            .manejaLotes(manejaLotes).build();
    }

    private EventoSyncRequest ticket() {
        return EventoSyncRequest.builder()
            .uuid(Uuid7.nuevo())
            .tipo(TipoEventoSync.CREAR)
            .ocurridoEn(anteayer)
            .secuencia(1)
            .idVendedor(ID_VENDEDOR)
            .idSesion(ID_SESION)
            .total(new BigDecimal("2400.10"))
            .metodoPago("EFECTIVO")
            .montoRecibido(new BigDecimal("3000.00"))
            .detalles(List.of(DetalleSyncRequest.builder()
                .tipo("PRODUCTO").idProducto(ID_PRODUCTO).cantidad(2)
                .precioUnitario(new BigDecimal("1200.05")).build()))
            .build();
    }
}
