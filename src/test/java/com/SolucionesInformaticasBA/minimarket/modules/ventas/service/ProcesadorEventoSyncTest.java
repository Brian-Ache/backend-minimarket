package com.SolucionesInformaticasBA.minimarket.modules.ventas.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import com.SolucionesInformaticasBA.minimarket.modules.caja.api.dto.CorteRequest;
import com.SolucionesInformaticasBA.minimarket.modules.caja.api.dto.SesionCajaResponse;
import com.SolucionesInformaticasBA.minimarket.modules.caja.enums.OrigenMovimientoCaja;
import com.SolucionesInformaticasBA.minimarket.modules.productos.api.ProductosApi;
import com.SolucionesInformaticasBA.minimarket.modules.productos.api.dto.ProductoResponse;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.enums.Rol;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.api.UsuarioApi;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.api.dto.DetalleSyncRequest;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.api.dto.EventoSyncRequest;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.api.dto.ResultadoEventoSync;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.entity.AnulacionPendiente;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.entity.DetalleVenta;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.entity.Venta;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.enums.CodigoErrorSync;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.enums.OrigenVenta;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.enums.TipoEventoSync;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.repository.AnulacionPendienteRepository;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.repository.DetalleVentaRepository;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.repository.VentaRepository;
import com.SolucionesInformaticasBA.minimarket.shared.exeption.BadRequestException;
import com.SolucionesInformaticasBA.minimarket.shared.exeption.ForbiddenException;
import com.SolucionesInformaticasBA.minimarket.shared.Uuid7;
import com.SolucionesInformaticasBA.minimarket.shared.exeption.ResourceNotFoundException;

/** Qué hace el backend con un ticket que se creó sin conexión y llega dos días después. */
@ExtendWith(MockitoExtension.class)
class ProcesadorEventoSyncTest {

    @Mock private VentaRepository ventaRepository;
    @Mock private DetalleVentaRepository detalleVentaRepository;
    @Mock private UsuarioApi usuarioApi;
    @Mock private ProductosApi productosApi;
    @Mock private CajaApi cajaApi;
    @Mock private DescontadorStock descontadorStock;
    @Mock private AnuladorVentas anuladorVentas;
    @Mock private AnulacionPendienteRepository anulacionPendienteRepository;

    @InjectMocks private ProcesadorEventoSync procesador;

    private static final UUID ID_VENDEDOR = UUID.randomUUID();
    private static final UUID ID_SYNC = UUID.randomUUID();
    private static final UUID ID_SESION = UUID.randomUUID();
    private static final UUID ID_PRODUCTO = UUID.fromString("11111111-1111-4111-8111-111111111111");

    @BeforeEach
    void configurarLimitesDeFecha() {
        ReflectionTestUtils.setField(procesador, "desfaseMaximoMinutos", 5L);
        ReflectionTestUtils.setField(procesador, "antiguedadMaximaDias", 60L);
    }

    // ------------------------------------------------------------------------------------
    // El camino feliz
    // ------------------------------------------------------------------------------------

    @Test
    @DisplayName("un ticket de anteayer se persiste con su fecha, su vendedor y su origen")
    void elTicketSePersisteComoOcurrio() {
        prepararTicketValido();
        LocalDateTime anteayer = LocalDateTime.now().minusDays(2);
        EventoSyncRequest evento = ticket(anteayer, new BigDecimal("2400.10"),
            linea(ID_PRODUCTO, 2, "1200.05"));

        ResultadoEventoSync resultado = procesador.procesar(evento, "caja-01", ID_SYNC);

        assertEquals("OK", resultado.getEstado());
        assertTrue(!resultado.isRequiereRevision());

        Venta venta = capturarVenta();
        assertEquals(evento.getUuid(), venta.getId(), "el id del ticket lo pone el front");
        assertEquals(anteayer, venta.getCreatedAt(), "la fecha es la del local, no la de llegada");
        assertEquals(ID_VENDEDOR, venta.getIdUsuario(), "el vendedor viaja en el payload");
        assertEquals(ID_SYNC, venta.getIdUsuarioSync(), "quién sincronizó sale del JWT");
        assertEquals(OrigenVenta.OFFLINE, venta.getOrigen());
        assertEquals("caja-01", venta.getDispositivo());
        assertEquals(ID_SESION, venta.getIdSesion());
        assertEquals(new BigDecimal("2400.10"), venta.getTotal());
        // El ticket llega cobrado: es lo que es un comprobante de caja registradora.
        assertEquals(true, venta.getCobrada());
        assertEquals(anteayer, venta.getFechaCobro());
        // Y queda el dato que no existía: cuándo llegó a MySQL.
        assertTrue(venta.getSincronizadoEn() != null);
    }

    @Test
    @DisplayName("las líneas llevan la fecha del ticket y el precio que se cobró ese día")
    void lasLineasLlevanLaFechaYElPrecioDelTicket() {
        prepararTicketValido();
        LocalDateTime anteayer = LocalDateTime.now().minusDays(2);
        // El producto hoy vale 2000; el ticket dice que ese día se cobró 1200.05.
        when(productosApi.getById(ID_PRODUCTO)).thenReturn(producto(2000f, 800f));

        procesador.procesar(ticket(anteayer, new BigDecimal("2400.10"),
            linea(ID_PRODUCTO, 2, "1200.05")), "caja-01", ID_SYNC);

        DetalleVenta detalle = capturarDetalles().get(0);
        assertEquals(new BigDecimal("1200.05"), detalle.getPrecioUnitario(),
            "el precio es el que pagó el cliente, no el de la lista de hoy");
        assertEquals(anteayer, detalle.getCreatedAt());
        assertTrue(Uuid7.esV7(detalle.getId()));
    }

    @Test
    @DisplayName("una venta en efectivo entra a la caja del turno que declara el ticket")
    void elEfectivoEntraAlTurnoDelTicket() {
        prepararTicketValido();
        procesador.procesar(ticket(LocalDateTime.now().minusHours(3), new BigDecimal("2400.10"),
            linea(ID_PRODUCTO, 2, "1200.05")), "caja-01", ID_SYNC);

        verify(cajaApi).registrarEntradaAutomatica(eqSesion(), any(), anyFloat(),
            any(OrigenMovimientoCaja.class), any(), any());
    }

    @Test
    @DisplayName("una venta con tarjeta no genera movimiento de caja: el arqueo cuenta billetes")
    void laTarjetaNoTocaLaCaja() {
        prepararTicketValido();
        EventoSyncRequest evento = ticket(LocalDateTime.now().minusHours(3),
            new BigDecimal("2400.10"), linea(ID_PRODUCTO, 2, "1200.05"));
        evento.setMetodoPago("TARJETA");

        procesador.procesar(evento, "caja-01", ID_SYNC);

        verify(cajaApi, never()).registrarEntradaAutomatica(any(), any(), anyFloat(), any(), any(), any());
    }

    // ------------------------------------------------------------------------------------
    // Idempotencia
    // ------------------------------------------------------------------------------------

    @Test
    @DisplayName("un CREAR repetido responde OK sin duplicar la venta ni tocar el stock")
    void elCrearRepetidoNoDuplicaNada() {
        EventoSyncRequest evento = ticket(LocalDateTime.now().minusDays(1),
            new BigDecimal("2400.10"), linea(ID_PRODUCTO, 2, "1200.05"));
        when(ventaRepository.findById(evento.getUuid()))
            .thenReturn(java.util.Optional.of(Venta.builder().id(evento.getUuid()).build()));

        ResultadoEventoSync resultado = procesador.procesar(evento, "caja-01", ID_SYNC);

        assertEquals("OK", resultado.getEstado());
        verify(ventaRepository, never()).save(any());
        verify(descontadorStock, never()).descontarRegularizando(any(),
            org.mockito.ArgumentMatchers.anyInt(), any(), any(), any());
        verify(cajaApi, never()).registrarEntradaAutomatica(any(), any(), anyFloat(), any(), any(), any());
    }

    @Test
    @DisplayName("el reenvío de un ticket marcado vuelve a decir que requiere revisión")
    void elReenvioConservaLaMarcaDeRevision() {
        EventoSyncRequest evento = ticket(LocalDateTime.now().minusDays(1),
            new BigDecimal("9999.99"), linea(ID_PRODUCTO, 2, "1200.05"));
        when(ventaRepository.findById(evento.getUuid())).thenReturn(java.util.Optional.of(
            Venta.builder().id(evento.getUuid()).requiereRevision(true).build()));

        // Si el reenvío devolviera un OK pelado, el único caso en que la idempotencia hace
        // falta —la respuesta original se perdió— sería el único en que el front nunca se
        // entera de que ese ticket quedó para revisar.
        assertTrue(procesador.procesar(evento, "caja-01", ID_SYNC).isRequiereRevision());
    }

    // ------------------------------------------------------------------------------------
    // El total (D9)
    // ------------------------------------------------------------------------------------

    @Test
    @DisplayName("un total que no coincide se persiste igual, con el recalculado y marcado")
    void elTotalQueNoCoincideSeMarca() {
        prepararTicketValido();
        // El front dice 9999.99; las líneas dan 2400.10.
        EventoSyncRequest evento = ticket(LocalDateTime.now().minusDays(1),
            new BigDecimal("9999.99"), linea(ID_PRODUCTO, 2, "1200.05"));

        ResultadoEventoSync resultado = procesador.procesar(evento, "caja-01", ID_SYNC);

        // Sigue siendo OK: la venta ocurrió y el evento sale de la cola del front.
        assertEquals("OK", resultado.getEstado());
        assertTrue(resultado.isRequiereRevision());
        assertTrue(resultado.getMensaje().contains("2400.10"));

        Venta venta = capturarVenta();
        assertEquals(new BigDecimal("2400.10"), venta.getTotal(), "se guarda el recalculado");
        assertTrue(venta.isRequiereRevision());
    }

    @Test
    @DisplayName("con DECIMAL ya no hace falta tolerancia: un centavo de diferencia es real")
    void unCentavoDeDiferenciaSeMarca() {
        prepararTicketValido();
        EventoSyncRequest evento = ticket(LocalDateTime.now().minusDays(1),
            new BigDecimal("2400.11"), linea(ID_PRODUCTO, 2, "1200.05"));

        assertTrue(procesador.procesar(evento, "caja-01", ID_SYNC).isRequiereRevision());
    }

    // ------------------------------------------------------------------------------------
    // Validaciones
    // ------------------------------------------------------------------------------------

    @Test
    @DisplayName("un uuid v4 se rechaza: fragmentaría el índice igual que sin validar")
    void elUuidV4SeRechaza() {
        EventoSyncRequest evento = ticket(LocalDateTime.now().minusDays(1),
            new BigDecimal("2400.10"), linea(ID_PRODUCTO, 2, "1200.05"));
        evento.setUuid(UUID.randomUUID());

        assertEquals(CodigoErrorSync.UUID_INVALIDO, error(evento).getCodigo());
    }

    @Test
    @DisplayName("una fecha en el futuro se rechaza: es un reloj mal puesto")
    void laFechaFuturaSeRechaza() {
        EventoSyncRequest evento = ticket(LocalDateTime.now().plusDays(3),
            new BigDecimal("2400.10"), linea(ID_PRODUCTO, 2, "1200.05"));

        EventoSyncException e = error(evento);
        assertEquals(CodigoErrorSync.FECHA_INVALIDA, e.getCodigo());
        assertTrue(!e.getCodigo().isReintentable(), "reintentarlo no va a cambiar el reloj");
    }

    @Test
    @DisplayName("una fecha de hace más de sesenta días se rechaza")
    void laFechaMuyViejaSeRechaza() {
        EventoSyncRequest evento = ticket(LocalDateTime.now().minusDays(61),
            new BigDecimal("2400.10"), linea(ID_PRODUCTO, 2, "1200.05"));

        assertEquals(CodigoErrorSync.FECHA_INVALIDA, error(evento).getCodigo());
    }

    @Test
    @DisplayName("un vendedor que no existe se rechaza, pero uno dado de baja entra igual")
    void elVendedorSoloTieneQueExistir() {
        EventoSyncRequest evento = ticket(LocalDateTime.now().minusDays(1),
            new BigDecimal("2400.10"), linea(ID_PRODUCTO, 2, "1200.05"));

        when(ventaRepository.findById(any())).thenReturn(java.util.Optional.empty());
        when(usuarioApi.existById(ID_VENDEDOR)).thenReturn(false);
        assertEquals(CodigoErrorSync.VENDEDOR_INEXISTENTE, error(evento).getCodigo());

        // existById no mira el estado: un empleado bloqueado durante el corte igual vendió, la
        // mercadería salió y la plata está en la caja.
        prepararTicketValido();
        assertEquals("OK", procesador.procesar(evento, "caja-01", ID_SYNC).getEstado());
    }

    @Test
    @DisplayName("un ticket sin sesión de caja no se puede colgar de ningún turno")
    void elTicketSinSesionSeRechaza() {
        EventoSyncRequest evento = ticket(LocalDateTime.now().minusDays(1),
            new BigDecimal("2400.10"), linea(ID_PRODUCTO, 2, "1200.05"));
        evento.setIdSesion(null);
        when(ventaRepository.findById(any())).thenReturn(java.util.Optional.empty());
        when(usuarioApi.existById(ID_VENDEDOR)).thenReturn(true);

        assertEquals(CodigoErrorSync.EVENTO_INVALIDO, error(evento).getCodigo());
    }

    @Test
    @DisplayName("una sesión que todavía no llegó es reintentable: puede venir en el lote siguiente")
    void laSesionQueNoLlegoEsReintentable() {
        EventoSyncRequest evento = ticket(LocalDateTime.now().minusDays(1),
            new BigDecimal("2400.10"), linea(ID_PRODUCTO, 2, "1200.05"));
        when(ventaRepository.findById(any())).thenReturn(java.util.Optional.empty());
        when(usuarioApi.existById(ID_VENDEDOR)).thenReturn(true);
        when(cajaApi.getSesionById(ID_SESION)).thenThrow(new ResourceNotFoundException("no está"));

        EventoSyncException e = error(evento);
        assertEquals(CodigoErrorSync.SESION_INEXISTENTE, e.getCodigo());
        assertTrue(e.getCodigo().isReintentable());
    }

    @Test
    @DisplayName("un ticket que llega después del corte de su turno no se cuela en el arqueo")
    void elTicketTardioNoEntraEnUnCorteFirmado() {
        EventoSyncRequest evento = ticket(LocalDateTime.now().minusDays(1),
            new BigDecimal("2400.10"), linea(ID_PRODUCTO, 2, "1200.05"));
        when(ventaRepository.findById(any())).thenReturn(java.util.Optional.empty());
        when(usuarioApi.existById(ID_VENDEDOR)).thenReturn(true);
        when(cajaApi.getSesionById(ID_SESION)).thenReturn(
            SesionCajaResponse.builder().id(ID_SESION).estado("CERRADA").build());

        EventoSyncException e = error(evento);
        assertEquals(CodigoErrorSync.SESION_CERRADA, e.getCodigo());
        assertTrue(!e.getCodigo().isReintentable(), "lo tiene que resolver el admin");
    }

    @Test
    @DisplayName("un producto que no existe se rechaza sin dejar la venta a medias")
    void elProductoInexistenteSeRechaza() {
        EventoSyncRequest evento = ticket(LocalDateTime.now().minusDays(1),
            new BigDecimal("2400.10"), linea(ID_PRODUCTO, 2, "1200.05"));
        prepararHastaLaSesion();
        when(ventaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(productosApi.getById(ID_PRODUCTO)).thenThrow(new ResourceNotFoundException("no está"));

        assertEquals(CodigoErrorSync.PRODUCTO_INEXISTENTE, error(evento).getCodigo());
        // La excepción sale del método transaccional, así que nada de lo escrito queda.
        verify(detalleVentaRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("un método de pago inventado se rechaza")
    void elMetodoDePagoInventadoSeRechaza() {
        EventoSyncRequest evento = ticket(LocalDateTime.now().minusDays(1),
            new BigDecimal("2400.10"), linea(ID_PRODUCTO, 2, "1200.05"));
        evento.setMetodoPago("CRIPTO");
        prepararHastaLaSesion();

        assertEquals(CodigoErrorSync.EVENTO_INVALIDO, error(evento).getCodigo());
    }

    @Test
    @DisplayName("un faltante de stock no rechaza el ticket: se regulariza y queda marcado")
    void elFaltanteDeStockSeRegulariza() {
        prepararTicketValido();
        // El sistema decía menos de lo que había: la venta física ya ocurrió y no se puede
        // rechazar por lo que diga el inventario.
        when(descontadorStock.descontarRegularizando(any(), org.mockito.ArgumentMatchers.anyInt(),
            any(), any(), any())).thenReturn(6);

        ResultadoEventoSync resultado = procesador.procesar(
            ticket(LocalDateTime.now().minusDays(1), new BigDecimal("2400.10"),
                linea(ID_PRODUCTO, 2, "1200.05")), "caja-01", ID_SYNC);

        assertEquals("OK", resultado.getEstado(), "el ticket entra igual");
        assertTrue(resultado.isRequiereRevision());
        assertTrue(resultado.getMensaje().contains("Se regularizaron 6 unidades de Yerba 1kg"),
            resultado.getMensaje());
        assertTrue(capturarVenta().isRequiereRevision());
    }

    @Test
    @DisplayName("la fecha del ticket baja hasta el FEFO, para no usar un lote que no existía")
    void laFechaDelTicketLlegaAlDescuento() {
        prepararTicketValido();
        LocalDateTime anteayer = LocalDateTime.now().minusDays(2);

        procesador.procesar(ticket(anteayer, new BigDecimal("2400.10"),
            linea(ID_PRODUCTO, 2, "1200.05")), "caja-01", ID_SYNC);

        verify(descontadorStock).descontarRegularizando(any(), org.mockito.ArgumentMatchers.eq(2),
            org.mockito.ArgumentMatchers.eq(ID_VENDEDOR), any(),
            org.mockito.ArgumentMatchers.eq(anteayer));
    }

    // ------------------------------------------------------------------------------------
    // Lo que todavía no está
    // ------------------------------------------------------------------------------------

    // ------------------------------------------------------------------------------------
    // El turno de caja
    // ------------------------------------------------------------------------------------

    @Test
    @DisplayName("la apertura offline crea el turno con el uuid y la fecha del dispositivo")
    void laAperturaUsaElUuidYLaFechaDelFront() {
        LocalDateTime anteayer = LocalDateTime.now().minusDays(2);
        EventoSyncRequest evento = apertura(anteayer, new BigDecimal("15000.00"));
        when(usuarioApi.existById(ID_VENDEDOR)).thenReturn(true);
        when(cajaApi.getSesionById(evento.getUuid())).thenThrow(new ResourceNotFoundException("no está"));

        assertEquals("OK", procesador.procesar(evento, "caja-01", ID_SYNC).getEstado());

        verify(cajaApi).abrirSesionSincronizada(evento.getUuid(), ID_VENDEDOR, 15000f, anteayer);
    }

    @Test
    @DisplayName("una apertura repetida no abre un segundo turno")
    void laAperturaRepetidaNoDuplica() {
        EventoSyncRequest evento = apertura(LocalDateTime.now().minusDays(2), new BigDecimal("15000.00"));
        when(usuarioApi.existById(ID_VENDEDOR)).thenReturn(true);
        when(cajaApi.getSesionById(evento.getUuid())).thenReturn(
            SesionCajaResponse.builder().id(evento.getUuid()).estado("ABIERTA").build());

        assertEquals("OK", procesador.procesar(evento, "caja-01", ID_SYNC).getEstado());

        verify(cajaApi, never()).abrirSesionSincronizada(any(), any(), anyFloat(), any());
    }

    @Test
    @DisplayName("si ya hay otro turno abierto la apertura es reintentable, no un error opaco")
    void laAperturaQueChocaEsReintentable() {
        EventoSyncRequest evento = apertura(LocalDateTime.now().minusDays(2), new BigDecimal("15000.00"));
        when(usuarioApi.existById(ID_VENDEDOR)).thenReturn(true);
        when(cajaApi.getSesionById(evento.getUuid())).thenThrow(new ResourceNotFoundException("no está"));
        org.mockito.Mockito.doThrow(new BadRequestException("Ya existe una sesión de caja abierta"))
            .when(cajaApi).abrirSesionSincronizada(any(), any(), anyFloat(), any());

        EventoSyncException e = error(evento);
        assertEquals(CodigoErrorSync.SESION_YA_ABIERTA, e.getCodigo());
        assertTrue(e.getCodigo().isReintentable(), "cuando el otro turno cierre, esta entra");
    }

    @Test
    @DisplayName("el corte lleva el conteo físico del cajero; el saldo esperado lo calcula el backend")
    void elCorteLlevaElConteoFisico() {
        LocalDateTime ayer = LocalDateTime.now().minusDays(1);
        EventoSyncRequest evento = corte(ayer, new BigDecimal("48500.00"), new BigDecimal("30000.00"));
        when(usuarioApi.existById(ID_VENDEDOR)).thenReturn(true);
        when(cajaApi.getSesionById(evento.getUuid())).thenReturn(SesionCajaResponse.builder()
            .id(evento.getUuid()).estado("ABIERTA")
            .fechaApertura(LocalDateTime.now().minusDays(2)).build());

        assertEquals("OK", procesador.procesar(evento, "caja-01", ID_SYNC).getEstado());

        ArgumentCaptor<CorteRequest> captor = ArgumentCaptor.forClass(CorteRequest.class);
        verify(cajaApi).realizarCorteSincronizado(
            org.mockito.ArgumentMatchers.eq(evento.getUuid()),
            org.mockito.ArgumentMatchers.eq(ID_VENDEDOR), captor.capture(),
            org.mockito.ArgumentMatchers.eq(ayer));
        assertEquals(48500f, captor.getValue().getSaldoReal());
        assertEquals(30000f, captor.getValue().getMontoRetirado());
        assertEquals("cerró el turno", captor.getValue().getObservaciones());
    }

    @Test
    @DisplayName("un corte repetido no recalcula el que ya está firmado")
    void elCorteRepetidoNoRecalcula() {
        EventoSyncRequest evento = corte(LocalDateTime.now().minusDays(1),
            new BigDecimal("48500.00"), BigDecimal.ZERO);
        when(usuarioApi.existById(ID_VENDEDOR)).thenReturn(true);
        when(cajaApi.getSesionById(evento.getUuid())).thenReturn(
            SesionCajaResponse.builder().id(evento.getUuid()).estado("CERRADA").build());

        assertEquals("OK", procesador.procesar(evento, "caja-01", ID_SYNC).getEstado());

        verify(cajaApi, never()).realizarCorteSincronizado(any(), any(), any(), any());
    }

    @Test
    @DisplayName("un corte de un turno que no llegó es reintentable")
    void elCorteSinSuAperturaEsReintentable() {
        EventoSyncRequest evento = corte(LocalDateTime.now().minusDays(1),
            new BigDecimal("48500.00"), BigDecimal.ZERO);
        when(usuarioApi.existById(ID_VENDEDOR)).thenReturn(true);
        when(cajaApi.getSesionById(evento.getUuid())).thenThrow(new ResourceNotFoundException("no está"));

        EventoSyncException e = error(evento);
        assertEquals(CodigoErrorSync.SESION_INEXISTENTE, e.getCodigo());
        assertTrue(e.getCodigo().isReintentable());
    }

    @Test
    @DisplayName("un corte anterior a la apertura de su propio turno es un reloj roto")
    void elCorteAnteriorALaApertura() {
        EventoSyncRequest evento = corte(LocalDateTime.now().minusDays(3),
            new BigDecimal("48500.00"), BigDecimal.ZERO);
        when(usuarioApi.existById(ID_VENDEDOR)).thenReturn(true);
        when(cajaApi.getSesionById(evento.getUuid())).thenReturn(SesionCajaResponse.builder()
            .id(evento.getUuid()).estado("ABIERTA")
            .fechaApertura(LocalDateTime.now().minusDays(2)).build());

        assertEquals(CodigoErrorSync.FECHA_INVALIDA, error(evento).getCodigo());
    }

    @Test
    @DisplayName("un evento de caja sin usuario no se puede atribuir a nadie")
    void elEventoDeCajaSinUsuario() {
        EventoSyncRequest evento = apertura(LocalDateTime.now().minusDays(2), new BigDecimal("15000.00"));
        evento.setIdUsuario(null);

        assertEquals(CodigoErrorSync.EVENTO_INVALIDO, error(evento).getCodigo());
    }

    private EventoSyncRequest apertura(LocalDateTime ocurridoEn, BigDecimal saldoInicial) {
        return EventoSyncRequest.builder()
            .uuid(Uuid7.nuevo()).tipo(TipoEventoSync.ABRIR_SESION).ocurridoEn(ocurridoEn)
            .secuencia(1).idUsuario(ID_VENDEDOR).saldoInicial(saldoInicial).build();
    }

    private EventoSyncRequest corte(LocalDateTime ocurridoEn, BigDecimal saldoFinal,
                                    BigDecimal montoRetirado) {
        return EventoSyncRequest.builder()
            .uuid(Uuid7.nuevo()).tipo(TipoEventoSync.CERRAR_SESION).ocurridoEn(ocurridoEn)
            .secuencia(99).idUsuario(ID_VENDEDOR).saldoFinal(saldoFinal)
            .montoRetirado(montoRetirado).observaciones("cerró el turno").build();
    }

    // ------------------------------------------------------------------------------------
    // ANULAR
    // ------------------------------------------------------------------------------------

    @Test
    @DisplayName("si el ticket ya está, se anula con la fecha que manda el front")
    void anulaElTicketYaPersistido() {
        Venta venta = ventaPersistida();
        EventoSyncRequest evento = anulacion(LocalDateTime.now().minusDays(1));
        when(ventaRepository.findById(evento.getUuid())).thenReturn(java.util.Optional.of(venta));
        when(usuarioApi.rolVigente(ID_VENDEDOR)).thenReturn(java.util.Optional.of(Rol.ADMIN));

        assertEquals("OK", procesador.procesar(evento, "caja-01", ID_SYNC).getEstado());

        verify(anuladorVentas).anular(venta, ID_VENDEDOR, evento.getOcurridoEn());
        verify(anulacionPendienteRepository, never()).save(any());
    }

    @Test
    @DisplayName("si el ticket todavía no llegó, la anulación queda esperándolo")
    void laAnulacionEsperaASuTicket() {
        EventoSyncRequest evento = anulacion(LocalDateTime.now().minusDays(1));
        when(ventaRepository.findById(evento.getUuid())).thenReturn(java.util.Optional.empty());

        // Responde OK igual: si no, el front tendría que reintentarlo y el reintento solo
        // funcionaría si los dos eventos cayeran en el mismo lote, que es lo que no pasó.
        assertEquals("OK", procesador.procesar(evento, "caja-01", ID_SYNC).getEstado());

        ArgumentCaptor<AnulacionPendiente> captor =
            ArgumentCaptor.forClass(AnulacionPendiente.class);
        verify(anulacionPendienteRepository).save(captor.capture());
        assertEquals(evento.getUuid(), captor.getValue().getIdVenta());
        assertEquals(evento.getOcurridoEn(), captor.getValue().getAnuladoEn());
        assertEquals(ID_VENDEDOR, captor.getValue().getIdUsuario());
        verify(anuladorVentas, never()).anular(any(), any(), any());
    }

    @Test
    @DisplayName("una venta ya anulada responde OK sin volver a tocar nada")
    void laVentaYaAnuladaNoSeToca() {
        Venta venta = ventaPersistida();
        venta.setDeletedAt(LocalDateTime.now().minusHours(1));
        EventoSyncRequest evento = anulacion(LocalDateTime.now().minusDays(1));
        when(ventaRepository.findById(evento.getUuid())).thenReturn(java.util.Optional.of(venta));

        assertEquals("OK", procesador.procesar(evento, "caja-01", ID_SYNC).getEstado());

        verify(anuladorVentas, never()).anular(any(), any(), any());
    }

    @Test
    @DisplayName("el permiso se resuelve sobre quien anuló en el local, no sobre quien sincroniza")
    void elPermisoEsDeQuienAnulo() {
        Venta venta = ventaPersistida();
        EventoSyncRequest evento = anulacion(LocalDateTime.now().minusDays(1));
        when(ventaRepository.findById(evento.getUuid())).thenReturn(java.util.Optional.of(venta));
        when(usuarioApi.rolVigente(ID_VENDEDOR)).thenReturn(java.util.Optional.of(Rol.EMPLEADO));

        procesador.procesar(evento, "caja-01", ID_SYNC);

        // ID_SYNC es quien sincroniza; el permiso se pregunta por ID_VENDEDOR, que es quien
        // anuló hace dos días en el local.
        verify(anuladorVentas).validarPermiso(venta, ID_VENDEDOR, false);
        verify(usuarioApi, never()).rolVigente(ID_SYNC);
    }

    @Test
    @DisplayName("un EMPLEADO que no puede anular esa venta recibe PERMISO_INSUFICIENTE")
    void elEmpleadoSinPermisoRebota() {
        Venta venta = ventaPersistida();
        EventoSyncRequest evento = anulacion(LocalDateTime.now().minusDays(1));
        when(ventaRepository.findById(evento.getUuid())).thenReturn(java.util.Optional.of(venta));
        when(usuarioApi.rolVigente(ID_VENDEDOR)).thenReturn(java.util.Optional.of(Rol.EMPLEADO));
        org.mockito.Mockito.doThrow(new ForbiddenException("no es tuya"))
            .when(anuladorVentas).validarPermiso(any(), any(), org.mockito.ArgumentMatchers.anyBoolean());

        EventoSyncException e = error(evento);
        assertEquals(CodigoErrorSync.PERMISO_INSUFICIENTE, e.getCodigo());
        assertTrue(!e.getCodigo().isReintentable(), "reintentarlo no le va a dar el permiso");
    }

    @Test
    @DisplayName("una anulación anterior a su propio ticket es un reloj roto")
    void laAnulacionAnteriorAlTicketSeRechaza() {
        Venta venta = ventaPersistida();
        venta.setCreatedAt(LocalDateTime.now().minusDays(1));
        EventoSyncRequest evento = anulacion(LocalDateTime.now().minusDays(3));
        when(ventaRepository.findById(evento.getUuid())).thenReturn(java.util.Optional.of(venta));
        when(usuarioApi.rolVigente(ID_VENDEDOR)).thenReturn(java.util.Optional.of(Rol.ADMIN));

        assertEquals(CodigoErrorSync.FECHA_INVALIDA, error(evento).getCodigo());
    }

    @Test
    @DisplayName("sin turno abierto la anulación es reintentable: cuando abran la caja, entra")
    void sinTurnoAbiertoEsReintentable() {
        Venta venta = ventaPersistida();
        EventoSyncRequest evento = anulacion(LocalDateTime.now().minusDays(1));
        when(ventaRepository.findById(evento.getUuid())).thenReturn(java.util.Optional.of(venta));
        when(usuarioApi.rolVigente(ID_VENDEDOR)).thenReturn(java.util.Optional.of(Rol.ADMIN));
        org.mockito.Mockito.doThrow(new BadRequestException("abrí la caja para devolver la plata"))
            .when(anuladorVentas).anular(any(), any(), any());

        EventoSyncException e = error(evento);
        assertEquals(CodigoErrorSync.SESION_CERRADA, e.getCodigo());
    }

    @Test
    @DisplayName("al persistirse el CREAR se aplica la anulación que estaba esperando")
    void elCrearAplicaLaAnulacionPendiente() {
        prepararTicketValido();
        EventoSyncRequest evento = ticket(LocalDateTime.now().minusDays(2),
            new BigDecimal("2400.10"), linea(ID_PRODUCTO, 2, "1200.05"));
        LocalDateTime anuladoEn = LocalDateTime.now().minusDays(1);
        when(anulacionPendienteRepository.findById(evento.getUuid())).thenReturn(
            java.util.Optional.of(AnulacionPendiente.builder()
                .idVenta(evento.getUuid()).anuladoEn(anuladoEn).idUsuario(ID_VENDEDOR).build()));
        when(usuarioApi.rolVigente(ID_VENDEDOR)).thenReturn(java.util.Optional.of(Rol.ADMIN));

        assertEquals("OK", procesador.procesar(evento, "caja-01", ID_SYNC).getEstado());

        // El ticket entra y sale anulado en la misma transacción: nunca queda vigente.
        verify(anuladorVentas).anular(any(Venta.class), org.mockito.ArgumentMatchers.eq(ID_VENDEDOR),
            org.mockito.ArgumentMatchers.eq(anuladoEn));
        verify(anulacionPendienteRepository).delete(any());
    }

    @Test
    @DisplayName("un CREAR sin anulación pendiente no consulta de más")
    void elCrearNormalNoAnulaNada() {
        prepararTicketValido();
        when(anulacionPendienteRepository.findById(any())).thenReturn(java.util.Optional.empty());

        procesador.procesar(ticket(LocalDateTime.now().minusDays(2), new BigDecimal("2400.10"),
            linea(ID_PRODUCTO, 2, "1200.05")), "caja-01", ID_SYNC);

        verify(anuladorVentas, never()).anular(any(), any(), any());
    }

    private Venta ventaPersistida() {
        return Venta.builder()
            .id(UUID.randomUUID())
            .idUsuario(ID_VENDEDOR)
            .idSesion(ID_SESION)
            .total(new BigDecimal("2400.10"))
            .cobrada(true)
            .metodoPago("EFECTIVO")
            .createdAt(LocalDateTime.now().minusDays(2))
            .build();
    }

    private EventoSyncRequest anulacion(LocalDateTime ocurridoEn) {
        return EventoSyncRequest.builder()
            .uuid(Uuid7.nuevo())
            .tipo(TipoEventoSync.ANULAR)
            .ocurridoEn(ocurridoEn)
            .secuencia(2)
            .idUsuario(ID_VENDEDOR)
            .motivo("el cliente se arrepintió")
            .build();
    }

    // ------------------------------------------------------------------------------------
    // Andamiaje
    // ------------------------------------------------------------------------------------

    private void prepararTicketValido() {
        prepararHastaLaSesion();
        when(ventaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(ventaRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(productosApi.getById(ID_PRODUCTO)).thenReturn(producto(1200.05f, 800f));
    }

    private void prepararHastaLaSesion() {
        lenient().when(ventaRepository.findById(any())).thenReturn(java.util.Optional.empty());
        lenient().when(usuarioApi.existById(ID_VENDEDOR)).thenReturn(true);
        lenient().when(cajaApi.getSesionById(ID_SESION)).thenReturn(
            SesionCajaResponse.builder().id(ID_SESION).estado("ABIERTA").build());
    }

    private EventoSyncException error(EventoSyncRequest evento) {
        return assertThrows(EventoSyncException.class,
            () -> procesador.procesar(evento, "caja-01", ID_SYNC));
    }

    private Venta capturarVenta() {
        ArgumentCaptor<Venta> captor = ArgumentCaptor.forClass(Venta.class);
        verify(ventaRepository).save(captor.capture());
        return captor.getValue();
    }

    @SuppressWarnings("unchecked")
    private List<DetalleVenta> capturarDetalles() {
        ArgumentCaptor<List<DetalleVenta>> captor = ArgumentCaptor.forClass(List.class);
        verify(detalleVentaRepository).saveAll(captor.capture());
        return captor.getValue();
    }

    private UUID eqSesion() {
        return org.mockito.ArgumentMatchers.eq(ID_SESION);
    }

    private ProductoResponse producto(float precio, float costo) {
        return ProductoResponse.builder()
            .id(ID_PRODUCTO).nombre("Yerba 1kg").precio(precio).costo(costo)
            .manejaLotes(false).build();
    }

    private DetalleSyncRequest linea(UUID idProducto, int cantidad, String precio) {
        return DetalleSyncRequest.builder()
            .tipo("PRODUCTO").idProducto(idProducto).cantidad(cantidad)
            .precioUnitario(new BigDecimal(precio)).build();
    }

    private EventoSyncRequest ticket(LocalDateTime ocurridoEn, BigDecimal total,
                                     DetalleSyncRequest... lineas) {
        return EventoSyncRequest.builder()
            .uuid(Uuid7.nuevo())
            .tipo(TipoEventoSync.CREAR)
            .ocurridoEn(ocurridoEn)
            .secuencia(1)
            .idVendedor(ID_VENDEDOR)
            .idSesion(ID_SESION)
            .total(total)
            .metodoPago("EFECTIVO")
            .montoRecibido(new BigDecimal("3000.00"))
            .detalles(new java.util.ArrayList<>(List.of(lineas)))
            .build();
    }
}
