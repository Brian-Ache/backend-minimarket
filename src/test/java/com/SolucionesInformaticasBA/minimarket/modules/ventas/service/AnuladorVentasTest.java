package com.SolucionesInformaticasBA.minimarket.modules.ventas.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
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
import com.SolucionesInformaticasBA.minimarket.modules.inventario.enums.TipoMovimiento;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.repository.LoteRepository;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.repository.MovimientoStockRepository;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.entity.Venta;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.repository.DetalleVentaRepository;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.repository.VentaRepository;
import com.SolucionesInformaticasBA.minimarket.shared.exeption.BadRequestException;
import com.SolucionesInformaticasBA.minimarket.shared.exeption.ForbiddenException;

/** Quién puede anular qué, y qué pasa con la mercadería y con la plata cuando se anula. */
@ExtendWith(MockitoExtension.class)
class AnuladorVentasTest {

    @Mock private VentaRepository ventaRepository;
    @Mock private DetalleVentaRepository detalleVentaRepository;
    @Mock private MovimientoStockRepository movimientoStockRepository;
    @Mock private LoteRepository loteRepository;
    @Mock private InventarioApi inventarioApi;
    @Mock private CajaApi cajaApi;

    @InjectMocks private AnuladorVentas anulador;

    private static final UUID ID_VENTA = UUID.randomUUID();
    private static final UUID ID_DUENIO = UUID.randomUUID();
    private static final UUID ID_OTRO = UUID.randomUUID();
    private static final UUID ID_SESION_VIEJA = UUID.randomUUID();
    private static final UUID ID_TURNO_DE_HOY = UUID.randomUUID();
    private static final UUID ID_PRODUCTO_A = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID ID_PRODUCTO_B = UUID.fromString("22222222-2222-4222-8222-222222222222");

    @BeforeEach
    void configurarVentana() {
        ReflectionTestUtils.setField(anulador, "diasParaAnular", 7L);
    }

    // ------------------------------------------------------------------------------------
    // Permisos (D5)
    // ------------------------------------------------------------------------------------

    @Test
    @DisplayName("un vendedor anula su propia venta dentro de la ventana")
    void elVendedorAnulaLaPropia() {
        when(cajaApi.getSesionById(ID_SESION_VIEJA)).thenReturn(sesionAbiertaHace(2));

        anulador.validarPermiso(ventaConSesion(), ID_DUENIO, false);
    }

    @Test
    @DisplayName("un vendedor no puede anular la venta de otro")
    void elVendedorNoAnulaLaAjena() {
        ForbiddenException e = assertThrows(ForbiddenException.class,
            () -> anulador.validarPermiso(ventaConSesion(), ID_OTRO, false));
        assertTrue(e.getMessage().contains("otro vendedor"));
    }

    @Test
    @DisplayName("un vendedor no puede anular una venta de hace ocho días")
    void elVendedorNoAnulaFueraDeLaVentana() {
        when(cajaApi.getSesionById(ID_SESION_VIEJA)).thenReturn(sesionAbiertaHace(8));

        BadRequestException e = assertThrows(BadRequestException.class,
            () -> anulador.validarPermiso(ventaConSesion(), ID_DUENIO, false));
        assertTrue(e.getMessage().contains("7"), e.getMessage());
    }

    @Test
    @DisplayName("un administrador anula cualquier venta y sin ventana")
    void elAdminNoTieneVentanaNiDuenio() {
        // Ni siquiera consulta la sesión: para un admin la ventana no existe.
        anulador.validarPermiso(ventaConSesion(), ID_OTRO, true);
        verify(cajaApi, never()).getSesionById(any());
    }

    @Test
    @DisplayName("la ventana se mide sobre la apertura del turno, no sobre la fecha de la venta")
    void laVentanaSeMideSobreElTurno() {
        // La venta es de hace 2 días pero su turno abrió hace 8: es el turno el que define el
        // período contable, así que ya está fuera de la ventana.
        Venta venta = ventaConSesion();
        venta.setCreatedAt(LocalDateTime.now().minusDays(2));
        when(cajaApi.getSesionById(ID_SESION_VIEJA)).thenReturn(sesionAbiertaHace(8));

        assertThrows(BadRequestException.class,
            () -> anulador.validarPermiso(venta, ID_DUENIO, false));
    }

    @Test
    @DisplayName("una venta sin sesión cae de vuelta en su propia fecha")
    void sinSesionValeLaFechaDeLaVenta() {
        Venta venta = ventaConSesion();
        venta.setIdSesion(null);
        venta.setCreatedAt(LocalDateTime.now().minusDays(8));

        assertThrows(BadRequestException.class,
            () -> anulador.validarPermiso(venta, ID_DUENIO, false));
    }

    // ------------------------------------------------------------------------------------
    // Reversa de caja
    // ------------------------------------------------------------------------------------

    @Test
    @DisplayName("la plata de una venta en efectivo vuelve por el turno abierto de HOY")
    void laReversaVaAlTurnoDeHoy() {
        Venta venta = ventaCobradaEnEfectivo();
        prepararAnulacionSinStock();
        when(cajaApi.buscarSesionActiva()).thenReturn(Optional.of(ID_TURNO_DE_HOY));

        anulador.anular(venta, ID_DUENIO, LocalDateTime.now());

        // Al turno de hoy, no al turno viejo en que se cobró: ese corte ya está firmado y la
        // plata sale físicamente de la caja de hoy.
        verify(cajaApi).registrarSalidaAutomatica(
            org.mockito.ArgumentMatchers.eq(ID_TURNO_DE_HOY),
            org.mockito.ArgumentMatchers.eq(ID_DUENIO),
            org.mockito.ArgumentMatchers.eq(1500f),
            org.mockito.ArgumentMatchers.eq(OrigenMovimientoCaja.REVERSA),
            org.mockito.ArgumentMatchers.eq(ID_VENTA), any());
    }

    @Test
    @DisplayName("sin turno abierto no se anula una venta en efectivo: no hay de dónde sacar la plata")
    void sinTurnoAbiertoNoSeAnula() {
        Venta venta = ventaCobradaEnEfectivo();
        lenient().when(movimientoStockRepository
            .findByIdReferenciaAndTipoAndDeletedAtIsNull(any(), any())).thenReturn(List.of());
        when(cajaApi.buscarSesionActiva()).thenReturn(Optional.empty());

        BadRequestException e = assertThrows(BadRequestException.class,
            () -> anulador.anular(venta, ID_DUENIO, LocalDateTime.now()));
        assertTrue(e.getMessage().contains("abrí la caja"), e.getMessage());

        // Y no queda anulada a medias.
        assertNull(venta.getDeletedAt());
        verify(ventaRepository, never()).save(any());
    }

    @Test
    @DisplayName("una venta con tarjeta no mueve la caja al anularse")
    void laTarjetaNoTocaLaCaja() {
        Venta venta = ventaCobradaEnEfectivo();
        venta.setMetodoPago("TARJETA");
        prepararAnulacionSinStock();

        anulador.anular(venta, ID_DUENIO, LocalDateTime.now());

        verify(cajaApi, never()).registrarSalidaAutomatica(any(), any(), anyFloat(), any(), any(), any());
    }

    @Test
    @DisplayName("una venta sin cobrar tampoco: nunca entró plata")
    void laVentaSinCobrarNoTocaLaCaja() {
        Venta venta = ventaCobradaEnEfectivo();
        venta.setCobrada(false);
        prepararAnulacionSinStock();

        anulador.anular(venta, null, LocalDateTime.now());

        verify(cajaApi, never()).registrarSalidaAutomatica(any(), any(), anyFloat(), any(), any(), any());
    }

    // ------------------------------------------------------------------------------------
    // Reversa de stock y fechas
    // ------------------------------------------------------------------------------------

    @Test
    @DisplayName("con el FEFO repartido entre lotes, cada lote recupera su cantidad exacta")
    void cadaLoteRecuperaLoSuyo() {
        Lote loteA = Lote.builder().id(UUID.randomUUID()).idProducto(ID_PRODUCTO_A).cantidad(0).build();
        Lote loteB = Lote.builder().id(UUID.randomUUID()).idProducto(ID_PRODUCTO_A).cantidad(5).build();

        Venta venta = ventaCobradaEnEfectivo();
        venta.setMetodoPago("TARJETA");
        when(movimientoStockRepository.findByIdReferenciaAndTipoAndDeletedAtIsNull(
                ID_VENTA, TipoMovimiento.VENTA)).thenReturn(List.of(
            MovimientoStock.builder().idProducto(ID_PRODUCTO_A).idLote(loteA.getId()).cantidad(-7).build(),
            MovimientoStock.builder().idProducto(ID_PRODUCTO_A).idLote(loteB.getId()).cantidad(-3).build()));
        when(loteRepository.findByIdParaActualizar(loteA.getId())).thenReturn(Optional.of(loteA));
        when(loteRepository.findByIdParaActualizar(loteB.getId())).thenReturn(Optional.of(loteB));
        when(detalleVentaRepository.findByIdVentaAndDeletedAtIsNull(ID_VENTA)).thenReturn(List.of());

        anulador.anular(venta, ID_DUENIO, LocalDateTime.now());

        // Trabajar sobre los movimientos y no sobre los detalles es lo que permite esto: el
        // detalle diría "10 unidades" sin decir de qué lote salió cada una.
        assertEquals(7, loteA.getCantidad());
        assertEquals(8, loteB.getCantidad());
    }

    @Test
    @DisplayName("un ticket con stock regularizado repone las 10 que salieron, no las 4 que había")
    void reponeLoQueSalioNoLoQueHabia() {
        Venta venta = ventaCobradaEnEfectivo();
        venta.setMetodoPago("TARJETA");
        // Lo que dejó la regularización de A5: la VENTA quedó registrada completa.
        when(movimientoStockRepository.findByIdReferenciaAndTipoAndDeletedAtIsNull(
                ID_VENTA, TipoMovimiento.VENTA)).thenReturn(List.of(
            MovimientoStock.builder().idProducto(ID_PRODUCTO_B).cantidad(-10).build()));
        when(detalleVentaRepository.findByIdVentaAndDeletedAtIsNull(ID_VENTA)).thenReturn(List.of());

        anulador.anular(venta, ID_DUENIO, LocalDateTime.now());

        ArgumentCaptor<MovimientoStockRequest> captor =
            ArgumentCaptor.forClass(MovimientoStockRequest.class);
        verify(inventarioApi).aumentar(captor.capture());
        assertEquals(10, captor.getValue().getCantidad(),
            "el AJUSTE de la regularización no se revierte: solo la VENTA");
    }

    @Test
    @DisplayName("la fecha de anulación es la que se pasa, no la de ahora: en offline la manda el front")
    void laFechaDeAnulacionLaPoneElLlamador() {
        Venta venta = ventaCobradaEnEfectivo();
        venta.setMetodoPago("TARJETA");
        prepararAnulacionSinStock();
        LocalDateTime anteayer = LocalDateTime.now().minusDays(2);

        anulador.anular(venta, ID_DUENIO, anteayer);

        assertEquals(anteayer, venta.getDeletedAt());
    }

    // ------------------------------------------------------------------------------------
    // Andamiaje
    // ------------------------------------------------------------------------------------

    private void prepararAnulacionSinStock() {
        when(movimientoStockRepository.findByIdReferenciaAndTipoAndDeletedAtIsNull(any(), any()))
            .thenReturn(List.of());
        when(detalleVentaRepository.findByIdVentaAndDeletedAtIsNull(ID_VENTA)).thenReturn(List.of());
    }

    private Venta ventaConSesion() {
        return Venta.builder()
            .id(ID_VENTA)
            .idUsuario(ID_DUENIO)
            .idSesion(ID_SESION_VIEJA)
            .total(new BigDecimal("1500.00"))
            .createdAt(LocalDateTime.now().minusDays(2))
            .build();
    }

    private Venta ventaCobradaEnEfectivo() {
        Venta venta = ventaConSesion();
        venta.setCobrada(true);
        venta.setMetodoPago("EFECTIVO");
        return venta;
    }

    private SesionCajaResponse sesionAbiertaHace(int dias) {
        return SesionCajaResponse.builder()
            .id(ID_SESION_VIEJA)
            .fechaApertura(LocalDateTime.now().minusDays(dias))
            .estado("CERRADA")
            .build();
    }
}
