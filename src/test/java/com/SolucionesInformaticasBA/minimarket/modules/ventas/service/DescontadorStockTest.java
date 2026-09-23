package com.SolucionesInformaticasBA.minimarket.modules.ventas.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
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

import com.SolucionesInformaticasBA.minimarket.modules.inventario.api.InventarioApi;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.entity.Lote;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.entity.MovimientoStock;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.enums.EstadoLote;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.enums.TipoMovimiento;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.repository.LoteRepository;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.repository.MovimientoStockRepository;
import com.SolucionesInformaticasBA.minimarket.modules.productos.api.dto.ProductoResponse;
import com.SolucionesInformaticasBA.minimarket.shared.exeption.BadRequestException;

/**
 * El descuento de stock de un producto por lotes, en sus dos modos: el de mostrador, que falla
 * si no alcanza, y el del ticket offline, que no puede fallar porque la mercadería salió hace
 * dos días.
 */
@ExtendWith(MockitoExtension.class)
class DescontadorStockTest {

    @Mock private InventarioApi inventarioApi;
    @Mock private LoteRepository loteRepository;
    @Mock private MovimientoStockRepository movimientoStockRepository;

    @InjectMocks private DescontadorStock descontador;

    private static final UUID ID_PRODUCTO = UUID.randomUUID();
    private static final UUID ID_USUARIO = UUID.randomUUID();
    private static final UUID ID_VENTA = UUID.randomUUID();

    // ------------------------------------------------------------------------------------
    // Venta de mostrador
    // ------------------------------------------------------------------------------------

    @Test
    @DisplayName("en el mostrador, si no alcanza el stock la venta falla")
    void enElMostradorFaltarStockEsUnError() {
        Lote unico = lote("L1", LocalDate.now().plusMonths(1), 4, hace(30));
        when(loteRepository.findParaDescuentoFefo(ID_PRODUCTO)).thenReturn(List.of(unico));

        // El cajero tiene la pantalla delante: puede contar, corregir o sacar la línea.
        assertThrows(BadRequestException.class,
            () -> descontador.descontar(producto(true), 10, ID_USUARIO, ID_VENTA));
    }

    // ------------------------------------------------------------------------------------
    // Ticket offline: regularización (D3)
    // ------------------------------------------------------------------------------------

    @Test
    @DisplayName("con 4 en el lote y un ticket de 10: se carga el faltante a ese mismo lote")
    void regularizaSobreElLoteQueElFefoEstabaConsumiendo() {
        Lote unico = lote("L1", LocalDate.now().plusMonths(1), 4, hace(30));
        when(loteRepository.findParaDescuentoFefo(ID_PRODUCTO)).thenReturn(List.of(unico));

        int regularizadas = descontador.descontarRegularizando(
            producto(true), 10, ID_USUARIO, ID_VENTA, hace(2));

        assertEquals(6, regularizadas);
        assertEquals(0, unico.getCantidad(), "el lote nunca queda negativo");

        List<MovimientoStock> movimientos = movimientos();
        // VENTA -4 (lo que había), AJUSTE +6 (lo que faltaba), VENTA -6 (el resto del ticket).
        assertEquals(3, movimientos.size());
        assertEquals(-4, movimientos.get(0).getCantidad());
        assertEquals(TipoMovimiento.AJUSTE, movimientos.get(1).getTipo());
        assertEquals(6, movimientos.get(1).getCantidad());
        assertTrue(movimientos.get(1).getMotivo().contains("Regularización por venta offline"));
        assertEquals(TipoMovimiento.VENTA, movimientos.get(2).getTipo());
        assertEquals(-6, movimientos.get(2).getCantidad());

        // Lo que la anulación va a reponer: la suma de los movimientos VENTA, que es el ticket
        // completo. Sin la regularización repondría 4 en vez de 10.
        int repondriaLaAnulacion = movimientos.stream()
            .filter(m -> m.getTipo() == TipoMovimiento.VENTA)
            .mapToInt(m -> Math.abs(m.getCantidad())).sum();
        assertEquals(10, repondriaLaAnulacion);

        // Y el kardex sigue sumando al stock: -4 +6 -6 = -4, lo que realmente bajó el lote.
        assertEquals(-4, movimientos.stream().mapToInt(MovimientoStock::getCantidad).sum());
    }

    @Test
    @DisplayName("un producto por lotes sin ningún lote cargado estrena uno SIN_FECHA")
    void sinNingunLoteSeCreaUnoSinFecha() {
        when(loteRepository.findParaDescuentoFefo(ID_PRODUCTO)).thenReturn(List.of());
        when(loteRepository.save(any())).thenAnswer(inv -> {
            Lote l = inv.getArgument(0);
            if (l.getId() == null) l.setId(UUID.randomUUID());
            return l;
        });

        int regularizadas = descontador.descontarRegularizando(
            producto(true), 10, ID_USUARIO, ID_VENTA, hace(2));

        assertEquals(10, regularizadas);

        ArgumentCaptor<Lote> captor = ArgumentCaptor.forClass(Lote.class);
        verify(loteRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        Lote creado = captor.getAllValues().get(0);
        // De esa mercadería no sabemos cuándo vence, y decirlo es lo honesto.
        assertEquals(EstadoLote.SIN_FECHA, creado.getEstado());
        assertEquals(null, creado.getFechaVencimiento());
        assertEquals(0, creado.getCantidad(), "nace en cero y el ajuste lo llena");
    }

    @Test
    @DisplayName("con stock de sobra no regulariza nada")
    void conStockSuficienteNoRegulariza() {
        Lote unico = lote("L1", LocalDate.now().plusMonths(1), 50, hace(30));
        when(loteRepository.findParaDescuentoFefo(ID_PRODUCTO)).thenReturn(List.of(unico));

        assertEquals(0, descontador.descontarRegularizando(
            producto(true), 10, ID_USUARIO, ID_VENTA, hace(2)));
        assertEquals(40, unico.getCantidad());
        assertEquals(1, movimientos().size());
    }

    // ------------------------------------------------------------------------------------
    // FEFO por fecha del ticket (D7)
    // ------------------------------------------------------------------------------------

    @Test
    @DisplayName("una venta de anteayer no se surte de un lote que ingresó ayer")
    void elLotePosteriorAlTicketNoSeUsa() {
        // El de ayer vence antes, así que el FEFO puro lo elegiría primero. Pero anteayer no
        // estaba en el local: físicamente no pudo haber surtido esa venta.
        Lote deAyer = lote("NUEVO", LocalDate.now().plusDays(10), 100, hace(1));
        Lote viejo = lote("VIEJO", LocalDate.now().plusMonths(6), 100, hace(30));
        when(loteRepository.findParaDescuentoFefo(ID_PRODUCTO)).thenReturn(List.of(deAyer, viejo));

        descontador.descontarRegularizando(producto(true), 10, ID_USUARIO, ID_VENTA, hace(2));

        assertEquals(100, deAyer.getCantidad(), "el lote de ayer queda intacto");
        assertEquals(90, viejo.getCantidad());
    }

    @Test
    @DisplayName("si los lotes viejos no alcanzan, recién ahí cae en los posteriores")
    void siLosViejosNoAlcanzanUsaLosPosteriores() {
        Lote deAyer = lote("NUEVO", LocalDate.now().plusDays(10), 100, hace(1));
        Lote viejo = lote("VIEJO", LocalDate.now().plusMonths(6), 4, hace(30));
        when(loteRepository.findParaDescuentoFefo(ID_PRODUCTO)).thenReturn(List.of(deAyer, viejo));

        int regularizadas = descontador.descontarRegularizando(
            producto(true), 10, ID_USUARIO, ID_VENTA, hace(2));

        assertEquals(0, regularizadas, "entre los dos lotes alcanza: no hay nada que regularizar");
        assertEquals(0, viejo.getCantidad());
        assertEquals(94, deAyer.getCantidad());
    }

    @Test
    @DisplayName("el orden de bloqueo no se toca: siempre una sola consulta, la de siempre")
    void noSeAgreganConsultasDeBloqueo() {
        Lote deAyer = lote("NUEVO", LocalDate.now().plusDays(10), 100, hace(1));
        Lote viejo = lote("VIEJO", LocalDate.now().plusMonths(6), 100, hace(30));
        when(loteRepository.findParaDescuentoFefo(ID_PRODUCTO)).thenReturn(List.of(deAyer, viejo));

        descontador.descontarRegularizando(producto(true), 10, ID_USUARIO, ID_VENTA, hace(2));

        // El filtro por fecha se hace en memoria sobre lo ya bloqueado. Una segunda consulta que
        // trajera "primero los viejos" tomaría los locks en un orden distinto al canónico, y ahí
        // se abre el ciclo de deadlock que el javadoc de esa consulta documenta.
        verify(loteRepository, org.mockito.Mockito.times(1)).findParaDescuentoFefo(ID_PRODUCTO);
        verify(loteRepository, never()).findByFechaVencimientoAfterAndDeletedAtIsNull(any());
        verify(loteRepository, never()).findAllByDeletedAtIsNull();
    }

    @Test
    @DisplayName("un lote sin created_at es de antes de la columna: se lo trata como viejo")
    void elLoteSinFechaDeAltaCuentaComoViejo() {
        Lote antiguo = lote("SIN_FECHA_ALTA", LocalDate.now().plusMonths(1), 100, null);
        when(loteRepository.findParaDescuentoFefo(ID_PRODUCTO)).thenReturn(List.of(antiguo));

        descontador.descontarRegularizando(producto(true), 10, ID_USUARIO, ID_VENTA, hace(2));

        assertEquals(90, antiguo.getCantidad());
    }

    @Test
    @DisplayName("la venta de mostrador no filtra por fecha: todo lote de hoy está disponible")
    void elMostradorNoFiltraPorFecha() {
        Lote reciente = lote("NUEVO", LocalDate.now().plusDays(10), 100, hace(0));
        when(loteRepository.findParaDescuentoFefo(ID_PRODUCTO)).thenReturn(List.of(reciente));

        descontador.descontar(producto(true), 10, ID_USUARIO, ID_VENTA);

        assertEquals(90, reciente.getCantidad());
    }

    // ------------------------------------------------------------------------------------
    // Andamiaje
    // ------------------------------------------------------------------------------------

    private LocalDateTime hace(int dias) {
        return LocalDateTime.now().minusDays(dias);
    }

    private Lote lote(String numero, LocalDate vencimiento, int cantidad, LocalDateTime creado) {
        return Lote.builder()
            .id(UUID.randomUUID())
            .idProducto(ID_PRODUCTO)
            .numeroLote(numero)
            .fechaVencimiento(vencimiento)
            .cantidad(cantidad)
            .createdAt(creado)
            .build();
    }

    private ProductoResponse producto(boolean manejaLotes) {
        return ProductoResponse.builder()
            .id(ID_PRODUCTO).nombre("Leche Entera 1L").precio(1400f).costo(900f)
            .manejaLotes(manejaLotes).build();
    }

    private List<MovimientoStock> movimientos() {
        ArgumentCaptor<MovimientoStock> captor = ArgumentCaptor.forClass(MovimientoStock.class);
        verify(movimientoStockRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        return captor.getAllValues();
    }
}
