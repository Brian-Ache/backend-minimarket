package com.SolucionesInformaticasBA.minimarket.modules.inventario.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.SolucionesInformaticasBA.minimarket.modules.inventario.api.dto.AjusteStockRequest;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.entity.MovimientoStock;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.entity.Stock;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.repository.LoteRepository;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.repository.MovimientoStockRepository;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.repository.StockRepository;
import com.SolucionesInformaticasBA.minimarket.modules.productos.api.ProductosApi;
import com.SolucionesInformaticasBA.minimarket.modules.productos.api.dto.ProductoResponse;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.api.UsuarioApi;
import com.SolucionesInformaticasBA.minimarket.shared.exeption.BadRequestException;

@ExtendWith(MockitoExtension.class)
class InventarioServiceAjusteTest {

    @Mock private StockRepository stockRepository;
    @Mock private LoteRepository loteRepository;
    @Mock private MovimientoStockRepository movimientoStockRepository;
    @Mock private ProductosApi productosApi;
    @Mock private UsuarioApi usuarioApi;

    @InjectMocks private InventarioService inventarioService;

    private static final UUID ID_PRODUCTO = UUID.randomUUID();
    private static final UUID ID_USUARIO = UUID.randomUUID();

    @Test
    @DisplayName("ajustar un producto sin fila de stock la crea, no responde 404")
    void ajusteCreaLaFilaDeStockSiFalta() {
        // getByIdProducto ya trata a un producto sin fila como stock 0; el ajuste respondía
        // 404 sobre ese mismo producto, así que el operador veía 0 y no podía corregirlo.
        when(usuarioApi.existById(ID_USUARIO)).thenReturn(true);
        when(productosApi.getById(ID_PRODUCTO)).thenReturn(producto(false));
        when(stockRepository.findByIdProductoParaActualizar(ID_PRODUCTO)).thenReturn(Optional.empty());
        when(stockRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        inventarioService.controlarStock(ID_USUARIO, ajuste(5));

        ArgumentCaptor<Stock> captor = ArgumentCaptor.forClass(Stock.class);
        verify(stockRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        assertEquals(5, captor.getValue().getCantidad());

        ArgumentCaptor<MovimientoStock> movimiento = ArgumentCaptor.forClass(MovimientoStock.class);
        verify(movimientoStockRepository).save(movimiento.capture());
        assertEquals(5, movimiento.getValue().getCantidad());
    }

    @Test
    @DisplayName("un producto que maneja lotes no se ajusta por stock, y el error dice por dónde sí")
    void ajusteDeProductoConLotesEsBadRequest() {
        when(usuarioApi.existById(ID_USUARIO)).thenReturn(true);
        when(productosApi.getById(ID_PRODUCTO)).thenReturn(producto(true));

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> inventarioService.controlarStock(ID_USUARIO, ajuste(5)));

        assertEquals("El producto maneja lotes: su existencia se ajusta por lote, "
                + "con POST /api/inventario/v1/lotes/ajustar",
                ex.getMessage());
        verify(stockRepository, never()).save(any());
        verify(movimientoStockRepository, never()).save(any());
    }

    @Test
    @DisplayName("no se puede borrar la fila de stock de un producto con existencias")
    void deleteConExistenciasEsBadRequest() {
        when(stockRepository.findByIdProductoParaActualizar(ID_PRODUCTO))
                .thenReturn(Optional.of(stockCon(4)));

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> inventarioService.delete(ID_PRODUCTO));

        assertEquals("No se puede borrar el stock de un producto con existencias: quedan 4 unidades. "
                + "Ajustá el stock a 0 antes de darlo de baja", ex.getMessage());
        verify(stockRepository, never()).save(any());
    }

    @Test
    @DisplayName("con el stock en cero la fila se da de baja")
    void deleteEnCeroDaDeBaja() {
        when(stockRepository.findByIdProductoParaActualizar(ID_PRODUCTO))
                .thenReturn(Optional.of(stockCon(0)));

        inventarioService.delete(ID_PRODUCTO);

        ArgumentCaptor<Stock> captor = ArgumentCaptor.forClass(Stock.class);
        verify(stockRepository).save(captor.capture());
        assertNotNull(captor.getValue().getDeletedAt());
    }

    private ProductoResponse producto(boolean manejaLotes) {
        return ProductoResponse.builder().id(ID_PRODUCTO).nombre("Leche").manejaLotes(manejaLotes).build();
    }

    private Stock stockCon(int cantidad) {
        return Stock.builder().id(UUID.randomUUID()).idProducto(ID_PRODUCTO).cantidad(cantidad).build();
    }

    private AjusteStockRequest ajuste(int stockReal) {
        return AjusteStockRequest.builder().idProducto(ID_PRODUCTO).stockReal(stockReal).build();
    }
}
