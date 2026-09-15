package com.SolucionesInformaticasBA.minimarket.modules.inventario.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.SolucionesInformaticasBA.minimarket.modules.inventario.api.dto.MovimientoStockRequest;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.entity.MovimientoStock;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.entity.Stock;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.enums.TipoMovimiento;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.repository.LoteRepository;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.repository.MovimientoStockRepository;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.repository.StockRepository;
import com.SolucionesInformaticasBA.minimarket.modules.productos.api.ProductosApi;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.api.UsuarioApi;

/**
 * El descuento de un ticket que ya ocurrió sin conexión: si el sistema decía 4 y se vendieron
 * 10, el que estaba mal era el sistema. La venta no produjo el faltante, lo reveló.
 */
@ExtendWith(MockitoExtension.class)
class InventarioServiceRegularizacionTest {

    @Mock private StockRepository stockRepository;
    @Mock private LoteRepository loteRepository;
    @Mock private MovimientoStockRepository movimientoStockRepository;
    @Mock private ProductosApi productosApi;
    @Mock private UsuarioApi usuarioApi;

    @InjectMocks private InventarioService inventarioService;

    private static final UUID ID_PRODUCTO = UUID.randomUUID();
    private static final UUID ID_USUARIO = UUID.randomUUID();
    private static final UUID ID_VENTA = UUID.randomUUID();

    @Test
    @DisplayName("con stock de sobra no regulariza nada: descuenta y listo")
    void conStockSuficienteNoRegulariza() {
        Stock stock = stock(50);
        when(stockRepository.findByIdProductoParaActualizar(ID_PRODUCTO)).thenReturn(Optional.of(stock));
        when(stockRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        int regularizadas = inventarioService.disminuirRegularizando(venta(10));

        assertEquals(0, regularizadas);
        assertEquals(40, stock.getCantidad());
        List<MovimientoStock> movimientos = movimientos();
        assertEquals(1, movimientos.size());
        assertEquals(TipoMovimiento.VENTA, movimientos.get(0).getTipo());
        assertEquals(-10, movimientos.get(0).getCantidad());
    }

    @Test
    @DisplayName("con 4 en stock y un ticket de 10: AJUSTE +6, VENTA -10 y el stock en 0")
    void regularizaElFaltanteYVendeCompleto() {
        Stock stock = stock(4);
        when(stockRepository.findByIdProductoParaActualizar(ID_PRODUCTO)).thenReturn(Optional.of(stock));
        when(stockRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        int regularizadas = inventarioService.disminuirRegularizando(venta(10));

        assertEquals(6, regularizadas);
        assertEquals(0, stock.getCantidad(), "no le debemos unidades a nadie: nunca queda negativo");

        List<MovimientoStock> movimientos = movimientos();
        assertEquals(2, movimientos.size());

        // El orden importa: primero entra lo que faltaba y después sale la venta completa. Al
        // revés, el stock pasaría por -6 en el paso intermedio.
        MovimientoStock ajuste = movimientos.get(0);
        assertEquals(TipoMovimiento.AJUSTE, ajuste.getTipo());
        assertEquals(6, ajuste.getCantidad());
        assertTrue(ajuste.getMotivo().contains("Regularización por venta offline"));
        assertTrue(ajuste.getMotivo().contains(ID_VENTA.toString()));

        MovimientoStock salida = movimientos.get(1);
        assertEquals(TipoMovimiento.VENTA, salida.getTipo());
        assertEquals(-10, salida.getCantidad(),
            "la VENTA es por el ticket completo: si no, la anulación repondría de menos");

        // El kardex sigue sumando al stock: +6 -10 = -4, que es exactamente lo que bajó.
        assertEquals(-4, movimientos.stream().mapToInt(MovimientoStock::getCantidad).sum());
    }

    @Test
    @DisplayName("con el stock en cero se regulariza el ticket entero")
    void regularizacionTotal() {
        Stock stock = stock(0);
        when(stockRepository.findByIdProductoParaActualizar(ID_PRODUCTO)).thenReturn(Optional.of(stock));
        when(stockRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertEquals(10, inventarioService.disminuirRegularizando(venta(10)));
        assertEquals(0, stock.getCantidad());
    }

    @Test
    @DisplayName("la fila de stock se toma con lock: leerla por fuera abriría una carrera")
    void tomaElLockDeLaFila() {
        Stock stock = stock(4);
        when(stockRepository.findByIdProductoParaActualizar(ID_PRODUCTO)).thenReturn(Optional.of(stock));
        when(stockRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        inventarioService.disminuirRegularizando(venta(10));

        // findByIdProductoParaActualizar es el SELECT ... FOR UPDATE; el otro finder no bloquea.
        verify(stockRepository).findByIdProductoParaActualizar(ID_PRODUCTO);
        verify(stockRepository, org.mockito.Mockito.never())
            .findByIdProductoAndDeletedAtIsNull(any());
    }

    private Stock stock(int cantidad) {
        return Stock.builder().id(UUID.randomUUID()).idProducto(ID_PRODUCTO).cantidad(cantidad).build();
    }

    private MovimientoStockRequest venta(int cantidad) {
        return MovimientoStockRequest.builder()
            .idProducto(ID_PRODUCTO)
            .cantidad(cantidad)
            .tipo("VENTA")
            .motivo("Venta realizada")
            .idUsuario(ID_USUARIO)
            .idReferencia(ID_VENTA)
            .build();
    }

    private List<MovimientoStock> movimientos() {
        ArgumentCaptor<MovimientoStock> captor = ArgumentCaptor.forClass(MovimientoStock.class);
        verify(movimientoStockRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        return captor.getAllValues();
    }
}
