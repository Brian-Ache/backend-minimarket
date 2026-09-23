package com.SolucionesInformaticasBA.minimarket.modules.inventario.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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

import com.SolucionesInformaticasBA.minimarket.modules.inventario.api.dto.AjusteLotesRequest;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.api.dto.ConteoLoteRequest;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.api.dto.LoteResponse;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.entity.Lote;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.entity.MovimientoStock;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.enums.EstadoLote;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.enums.TipoMovimiento;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.repository.LoteRepository;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.repository.MovimientoStockRepository;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.repository.StockRepository;
import com.SolucionesInformaticasBA.minimarket.modules.productos.api.ProductosApi;
import com.SolucionesInformaticasBA.minimarket.modules.productos.api.dto.ProductoResponse;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.api.UsuarioApi;
import com.SolucionesInformaticasBA.minimarket.shared.exeption.BadRequestException;
import com.SolucionesInformaticasBA.minimarket.shared.exeption.ResourceNotFoundException;

@ExtendWith(MockitoExtension.class)
class InventarioServiceAjusteLotesTest {

    @Mock private StockRepository stockRepository;
    @Mock private LoteRepository loteRepository;
    @Mock private MovimientoStockRepository movimientoStockRepository;
    @Mock private ProductosApi productosApi;
    @Mock private UsuarioApi usuarioApi;

    @InjectMocks private InventarioService inventarioService;

    private static final UUID ID_PRODUCTO = UUID.randomUUID();
    private static final UUID ID_USUARIO = UUID.randomUUID();
    private static final UUID ID_LOTE_A = UUID.randomUUID();
    private static final UUID ID_LOTE_B = UUID.randomUUID();
    private static final UUID ID_LOTE_C = UUID.randomUUID();

    @Test
    @DisplayName("el ajuste es parcial: el lote que no se contó queda intacto")
    void elLoteNoDeclaradoNoSeToca() {
        stubProductoConLotes();
        Lote a = lote(ID_LOTE_A, 10);
        Lote b = lote(ID_LOTE_B, 3);
        Lote c = lote(ID_LOTE_C, 7);
        when(loteRepository.findParaDescuentoFefo(ID_PRODUCTO)).thenReturn(List.of(a, b, c));

        inventarioService.ajustarLotes(ID_USUARIO, ajuste(
                conteo(ID_LOTE_A, 5),
                conteo(ID_LOTE_B, 0)));

        assertEquals(5, a.getCantidad());
        assertEquals(0, b.getCantidad());
        assertEquals(7, c.getCantidad());
        verify(loteRepository, times(2)).save(any());
    }

    @Test
    @DisplayName("cada lote corregido deja su movimiento, con el lote y la diferencia firmada")
    void cadaDiferenciaDejaSuMovimiento() {
        stubProductoConLotes();
        when(loteRepository.findParaDescuentoFefo(ID_PRODUCTO))
                .thenReturn(List.of(lote(ID_LOTE_A, 10), lote(ID_LOTE_B, 3)));

        inventarioService.ajustarLotes(ID_USUARIO, ajuste(
                conteo(ID_LOTE_A, 5),
                conteo(ID_LOTE_B, 8)));

        List<MovimientoStock> movimientos = movimientosGuardados();
        assertEquals(2, movimientos.size());
        assertEquals(ID_LOTE_A, movimientos.get(0).getIdLote());
        assertEquals(-5, movimientos.get(0).getCantidad());
        assertEquals(ID_LOTE_B, movimientos.get(1).getIdLote());
        assertEquals(5, movimientos.get(1).getCantidad());
        movimientos.forEach(m -> {
            assertEquals(TipoMovimiento.AJUSTE, m.getTipo());
            assertEquals(ID_USUARIO, m.getIdUsuario());
        });
    }

    @Test
    @DisplayName("un conteo sin diferencias igual queda registrado, una sola vez y sin lote")
    void elControlSinDiferenciasQuedaRegistrado() {
        stubProductoConLotes();
        when(loteRepository.findParaDescuentoFefo(ID_PRODUCTO))
                .thenReturn(List.of(lote(ID_LOTE_A, 10), lote(ID_LOTE_B, 3)));

        inventarioService.ajustarLotes(ID_USUARIO, ajuste(
                conteo(ID_LOTE_A, 10),
                conteo(ID_LOTE_B, 3)));

        List<MovimientoStock> movimientos = movimientosGuardados();
        assertEquals(1, movimientos.size());
        assertEquals(0, movimientos.get(0).getCantidad());
        assertNull(movimientos.get(0).getIdLote());
        assertTrue(movimientos.get(0).getMotivo().contains("sin diferencias"),
                movimientos.get(0).getMotivo());
        verify(loteRepository, never()).save(any());
    }

    @Test
    @DisplayName("los lotes se bloquean todos juntos y en el orden del FEFO, no uno por uno")
    void bloqueaPorLaUnicaPuertaDeBloqueo() {
        stubProductoConLotes();
        when(loteRepository.findParaDescuentoFefo(ID_PRODUCTO))
                .thenReturn(List.of(lote(ID_LOTE_A, 10), lote(ID_LOTE_B, 3)));

        inventarioService.ajustarLotes(ID_USUARIO, ajuste(
                conteo(ID_LOTE_B, 1),
                conteo(ID_LOTE_A, 9)));

        // Tomarlos en el orden en que los mandó el cliente los bloquearía distinto que las
        // ventas y las anulaciones, que es lo que abre el ciclo.
        verify(loteRepository, times(1)).findParaDescuentoFefo(ID_PRODUCTO);
        verify(loteRepository, never()).findByIdParaActualizar(any());
    }

    @Test
    @DisplayName("un producto que no maneja lotes se ajusta por /controlar")
    void productoSinLotesEsBadRequest() {
        when(usuarioApi.existById(ID_USUARIO)).thenReturn(true);
        when(productosApi.getById(ID_PRODUCTO)).thenReturn(
                ProductoResponse.builder().id(ID_PRODUCTO).nombre("Agua").manejaLotes(false).build());

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> inventarioService.ajustarLotes(ID_USUARIO, ajuste(conteo(ID_LOTE_A, 5))));

        assertTrue(ex.getMessage().contains("/controlar"), ex.getMessage());
        verify(loteRepository, never()).save(any());
    }

    @Test
    @DisplayName("un lote de otro producto no se ajusta, y no deja aplicadas las líneas anteriores")
    void loteAjenoEsBadRequestYNoAplicaNada() {
        stubProductoConLotes();
        when(loteRepository.findParaDescuentoFefo(ID_PRODUCTO)).thenReturn(List.of(lote(ID_LOTE_A, 10)));

        UUID ajeno = UUID.randomUUID();
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> inventarioService.ajustarLotes(ID_USUARIO, ajuste(
                        conteo(ID_LOTE_A, 5),
                        conteo(ajeno, 2))));

        assertTrue(ex.getMessage().contains(ajeno.toString()), ex.getMessage());
        verify(loteRepository, never()).save(any());
        verify(movimientoStockRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("el mismo lote contado dos veces no tiene resolución posible")
    void loteRepetidoEsBadRequest() {
        stubProductoConLotes();
        when(loteRepository.findParaDescuentoFefo(ID_PRODUCTO)).thenReturn(List.of(lote(ID_LOTE_A, 10)));

        assertThrows(BadRequestException.class,
                () -> inventarioService.ajustarLotes(ID_USUARIO, ajuste(
                        conteo(ID_LOTE_A, 5),
                        conteo(ID_LOTE_A, 6))));

        verify(loteRepository, never()).save(any());
    }

    @Test
    @DisplayName("ajustar con un usuario inexistente da 404 y no un 500")
    void usuarioInexistenteEsNotFound() {
        when(usuarioApi.existById(ID_USUARIO)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class,
                () -> inventarioService.ajustarLotes(ID_USUARIO, ajuste(conteo(ID_LOTE_A, 5))));

        verify(loteRepository, never()).save(any());
    }

    @Test
    @DisplayName("los lotes ajustables se piden con el corte de vencidos y de agotados viejos")
    void ajustablesUsaLosDosCortes() {
        when(productosApi.getById(ID_PRODUCTO)).thenReturn(
                ProductoResponse.builder().id(ID_PRODUCTO).nombre("Leche").manejaLotes(true).build());
        when(loteRepository.findAjustables(any(), any(), any()))
                .thenReturn(List.of(lote(ID_LOTE_A, 4)));

        List<LoteResponse> lotes = inventarioService.getLotesAjustables(ID_PRODUCTO);

        assertEquals(1, lotes.size());
        assertEquals("Leche", lotes.get(0).getNombreProducto());

        ArgumentCaptor<LocalDate> hoy = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDateTime> agotadoDesde = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(loteRepository).findAjustables(any(), hoy.capture(), agotadoDesde.capture());

        assertEquals(LocalDate.now(), hoy.getValue());
        // El corte de agotados sale de la constante, no de un número suelto en el servicio.
        assertEquals(EstadoLote.DIAS_LOTE_AGOTADO,
                java.time.temporal.ChronoUnit.DAYS.between(agotadoDesde.getValue(), LocalDateTime.now()));
    }

    @Test
    @DisplayName("pedir los ajustables de un producto sin lotes es 400")
    void ajustablesDeProductoSinLotesEsBadRequest() {
        when(productosApi.getById(ID_PRODUCTO)).thenReturn(
                ProductoResponse.builder().id(ID_PRODUCTO).nombre("Agua").manejaLotes(false).build());

        assertThrows(BadRequestException.class, () -> inventarioService.getLotesAjustables(ID_PRODUCTO));
    }

    // Helpers

    private void stubProductoConLotes() {
        when(usuarioApi.existById(ID_USUARIO)).thenReturn(true);
        when(productosApi.getById(ID_PRODUCTO)).thenReturn(
                ProductoResponse.builder().id(ID_PRODUCTO).nombre("Leche").manejaLotes(true).build());
    }

    private List<MovimientoStock> movimientosGuardados() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<MovimientoStock>> captor = ArgumentCaptor.forClass(List.class);
        verify(movimientoStockRepository).saveAll(captor.capture());
        return captor.getValue();
    }

    private AjusteLotesRequest ajuste(ConteoLoteRequest... conteos) {
        return AjusteLotesRequest.builder()
                .idProducto(ID_PRODUCTO)
                .conteos(List.of(conteos))
                .build();
    }

    private ConteoLoteRequest conteo(UUID idLote, int cantidadReal) {
        return ConteoLoteRequest.builder().idLote(idLote).cantidadReal(cantidadReal).build();
    }

    private Lote lote(UUID id, int cantidad) {
        return Lote.builder()
                .id(id)
                .idProducto(ID_PRODUCTO)
                .numeroLote("L-" + cantidad)
                .fechaVencimiento(LocalDate.now().plusMonths(2))
                .cantidad(cantidad)
                .build();
    }
}
