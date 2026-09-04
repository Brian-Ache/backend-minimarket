package com.SolucionesInformaticasBA.minimarket.modules.inventario.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.SolucionesInformaticasBA.minimarket.modules.inventario.api.dto.AjusteStockRequest;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.api.dto.MovimientoStockRequest;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.entity.Stock;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.repository.LoteRepository;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.repository.MovimientoStockRepository;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.repository.StockRepository;
import com.SolucionesInformaticasBA.minimarket.modules.productos.api.ProductosApi;
import com.SolucionesInformaticasBA.minimarket.modules.productos.api.dto.ProductoResponse;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.api.UsuarioApi;
import com.SolucionesInformaticasBA.minimarket.shared.exeption.BadRequestException;

/**
 * Toda escritura sobre la cantidad de stock tiene que leer la fila con el lock tomado. Es un
 * test de interacción a propósito: el lost update no se puede reproducir sin base, pero sí se
 * puede fijar que ningún camino de escritura vuelva al finder sin bloqueo.
 */
@ExtendWith(MockitoExtension.class)
class InventarioServiceConcurrenciaTest {

    @Mock private StockRepository stockRepository;
    @Mock private LoteRepository loteRepository;
    @Mock private MovimientoStockRepository movimientoStockRepository;
    @Mock private ProductosApi productosApi;
    @Mock private UsuarioApi usuarioApi;

    @InjectMocks private InventarioService inventarioService;

    private static final UUID ID_PRODUCTO = UUID.randomUUID();
    private static final UUID ID_USUARIO = UUID.randomUUID();

    @Test
    @DisplayName("aumentar lee el stock con el lock de la fila tomado")
    void aumentarTomaElLock() {
        when(stockRepository.findByIdProductoParaActualizar(ID_PRODUCTO))
                .thenReturn(Optional.of(stockCon(10)));

        inventarioService.aumentar(movimiento(5));

        verify(stockRepository).findByIdProductoParaActualizar(ID_PRODUCTO);
        verify(stockRepository, never()).findByIdProductoAndDeletedAtIsNull(any());
    }

    @Test
    @DisplayName("disminuir lee el stock con el lock de la fila tomado")
    void disminuirTomaElLock() {
        when(stockRepository.findByIdProductoParaActualizar(ID_PRODUCTO))
                .thenReturn(Optional.of(stockCon(10)));

        inventarioService.disminuir(movimiento(4));

        verify(stockRepository).findByIdProductoParaActualizar(ID_PRODUCTO);
        verify(stockRepository, never()).findByIdProductoAndDeletedAtIsNull(any());
    }

    @Test
    @DisplayName("el ajuste manual también lee con lock")
    void controlarStockTomaElLock() {
        when(usuarioApi.existById(ID_USUARIO)).thenReturn(true);
        when(productosApi.getById(ID_PRODUCTO)).thenReturn(productoComun());
        when(stockRepository.findByIdProductoParaActualizar(ID_PRODUCTO))
                .thenReturn(Optional.of(stockCon(10)));

        inventarioService.controlarStock(ID_USUARIO, ajuste(7));

        verify(stockRepository).findByIdProductoParaActualizar(ID_PRODUCTO);
        verify(stockRepository, never()).findByIdProductoAndDeletedAtIsNull(any());
    }

    @Test
    @DisplayName("el descuento sigue rechazando dejar el stock en negativo")
    void disminuirNoDejaStockNegativo() {
        when(stockRepository.findByIdProductoParaActualizar(ID_PRODUCTO))
                .thenReturn(Optional.of(stockCon(3)));

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> inventarioService.disminuir(movimiento(4)));

        assertEquals("Stock insuficiente. Disponible: 3, solicitado: 4", ex.getMessage());
        verify(stockRepository, never()).save(any());
    }

    private ProductoResponse productoComun() {
        return ProductoResponse.builder().id(ID_PRODUCTO).nombre("Agua").manejaLotes(false).build();
    }

    private Stock stockCon(int cantidad) {
        return Stock.builder().id(UUID.randomUUID()).idProducto(ID_PRODUCTO).cantidad(cantidad).build();
    }

    private MovimientoStockRequest movimiento(int cantidad) {
        return MovimientoStockRequest.builder()
                .idProducto(ID_PRODUCTO)
                .cantidad(cantidad)
                .tipo("AJUSTE")
                .idUsuario(ID_USUARIO)
                .build();
    }

    private AjusteStockRequest ajuste(int stockReal) {
        return AjusteStockRequest.builder()
                .idProducto(ID_PRODUCTO)
                .stockReal(stockReal)
                .build();
    }
}
