package com.SolucionesInformaticasBA.minimarket.modules.productos.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.SolucionesInformaticasBA.minimarket.modules.categorias.api.CategoriasApi;
import com.SolucionesInformaticasBA.minimarket.modules.categorias.api.dto.CategoriaResponse;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.entity.Lote;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.entity.Stock;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.repository.LoteRepository;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.repository.StockRepository;
import com.SolucionesInformaticasBA.minimarket.modules.productos.api.dto.ProductoRequest;
import com.SolucionesInformaticasBA.minimarket.modules.productos.api.dto.ProductoResponse;
import com.SolucionesInformaticasBA.minimarket.modules.productos.entity.Producto;
import com.SolucionesInformaticasBA.minimarket.modules.productos.repository.ProductoRepository;
import com.SolucionesInformaticasBA.minimarket.modules.proveedores.api.ProveedoresApi;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.api.UsuarioApi;
import com.SolucionesInformaticasBA.minimarket.shared.exeption.BadRequestException;
import com.SolucionesInformaticasBA.minimarket.shared.exeption.ResourceNotFoundException;

@ExtendWith(MockitoExtension.class)
class ProductoServiceTest {

    @Mock private ProductoRepository productoRepository;
    @Mock private StockRepository stockRepository;
    @Mock private LoteRepository loteRepository;
    @Mock private UsuarioApi usuarioApi;
    @Mock private CategoriasApi categoriasApi;
    @Mock private ProveedoresApi proveedoresApi;

    @InjectMocks private ProductoService productoService;

    private static final UUID ID_PRODUCTO = UUID.randomUUID();
    private static final UUID ID_CATEGORIA = UUID.randomUUID();

    @Test
    @DisplayName("crear con un usuario inexistente da 404 y no un 500")
    void crearSinUsuarioEsNotFound() {
        UUID idUsuario = UUID.randomUUID();
        when(usuarioApi.existById(idUsuario)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class,
                () -> productoService.crear(idUsuario, requestCompleto()));

        verify(productoRepository, never()).save(any());
    }

    @Test
    @DisplayName("un producto borrado no se puede editar")
    void updateDeProductoBorradoEsNotFound() {
        when(productoRepository.findByIdAndDeletedAtIsNull(ID_PRODUCTO)).thenReturn(null);

        assertThrows(ResourceNotFoundException.class,
                () -> productoService.update(ID_PRODUCTO, requestCompleto()));

        verify(productoRepository, never()).save(any());
    }

    @Test
    @DisplayName("el PUT desasigna categoría, proveedor, costo y margen cuando vienen nulos")
    void updateLimpiaLosCamposOpcionales() {
        Producto existente = productoConCategoria();
        existente.setCosto(1500f);
        existente.setMargen(40f);
        existente.setIdProveedor(UUID.randomUUID());
        when(productoRepository.findByIdAndDeletedAtIsNull(ID_PRODUCTO)).thenReturn(existente);
        when(productoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ProductoRequest sinOpcionales = ProductoRequest.builder()
                .nombre("Coca Cola 2.25L")
                .barcode("7790001000056")
                .precio(3200f)
                .build();

        ProductoResponse response = productoService.update(ID_PRODUCTO, sinOpcionales);

        ArgumentCaptor<Producto> captor = ArgumentCaptor.forClass(Producto.class);
        verify(productoRepository).save(captor.capture());
        Producto guardado = captor.getValue();

        assertNull(guardado.getIdCategoria());
        assertNull(guardado.getIdProveedor());
        assertNull(guardado.getCosto());
        assertNull(guardado.getMargen());
        assertNull(response.getCategoria());
        assertNull(response.getProveedor());
    }

    @Test
    @DisplayName("editar un producto con barcode nulo en la base no rompe")
    void updateConBarcodeNuloEnLaEntidad() {
        Producto existente = productoConCategoria();
        existente.setBarcode(null);
        existente.setIdCategoria(null);
        when(productoRepository.findByIdAndDeletedAtIsNull(ID_PRODUCTO)).thenReturn(existente);
        when(productoRepository.findByBarcodeAndDeletedAtIsNull("7790001000056")).thenReturn(null);
        when(productoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ProductoResponse response = productoService.update(ID_PRODUCTO, ProductoRequest.builder()
                .nombre("Coca Cola 2.25L")
                .barcode("7790001000056")
                .precio(3200f)
                .build());

        assertEquals("7790001000056", response.getBarcode());
    }

    @Test
    @DisplayName("borrar el producto también da de baja su fila de stock")
    void deleteDaDeBajaElStock() {
        when(productoRepository.findByIdAndDeletedAtIsNull(ID_PRODUCTO)).thenReturn(productoConCategoria());
        Stock stock = Stock.builder().id(UUID.randomUUID()).idProducto(ID_PRODUCTO).cantidad(0).build();
        when(stockRepository.findByIdProductoAndDeletedAtIsNull(ID_PRODUCTO)).thenReturn(Optional.of(stock));

        productoService.delete(ID_PRODUCTO);

        ArgumentCaptor<Stock> captor = ArgumentCaptor.forClass(Stock.class);
        verify(stockRepository).save(captor.capture());
        assertNotNull(captor.getValue().getDeletedAt());
    }

    @Test
    @DisplayName("borrar el producto también da de baja sus lotes")
    void deleteDaDeBajaLosLotes() {
        when(productoRepository.findByIdAndDeletedAtIsNull(ID_PRODUCTO)).thenReturn(productoConCategoria());
        when(stockRepository.findByIdProductoAndDeletedAtIsNull(ID_PRODUCTO)).thenReturn(Optional.empty());
        when(loteRepository.findByIdProductoAndDeletedAtIsNull(ID_PRODUCTO)).thenReturn(List.of(
                Lote.builder().id(UUID.randomUUID()).idProducto(ID_PRODUCTO).cantidad(0).build(),
                Lote.builder().id(UUID.randomUUID()).idProducto(ID_PRODUCTO).cantidad(0).build()));

        productoService.delete(ID_PRODUCTO);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Lote>> captor = ArgumentCaptor.forClass(List.class);
        verify(loteRepository).saveAll(captor.capture());
        List<Lote> guardados = captor.getValue();

        assertEquals(2, guardados.size());
        guardados.forEach(lote -> assertNotNull(lote.getDeletedAt()));
    }

    @Test
    @DisplayName("un producto con stock no se puede borrar")
    void deleteConStockEsBadRequest() {
        when(productoRepository.findByIdAndDeletedAtIsNull(ID_PRODUCTO)).thenReturn(productoConCategoria());
        when(stockRepository.findByIdProductoAndDeletedAtIsNull(ID_PRODUCTO)).thenReturn(Optional.of(
                Stock.builder().id(UUID.randomUUID()).idProducto(ID_PRODUCTO).cantidad(7).build()));

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> productoService.delete(ID_PRODUCTO));

        assertTrue(ex.getMessage().contains("7 unidades en stock"), ex.getMessage());
        verify(productoRepository, never()).save(any());
        verify(stockRepository, never()).save(any());
    }

    @Test
    @DisplayName("un producto con lotes cargados no se puede borrar")
    void deleteConLotesEsBadRequest() {
        when(productoRepository.findByIdAndDeletedAtIsNull(ID_PRODUCTO)).thenReturn(productoConCategoria());
        when(stockRepository.findByIdProductoAndDeletedAtIsNull(ID_PRODUCTO)).thenReturn(Optional.empty());
        when(loteRepository.findByIdProductoAndDeletedAtIsNull(ID_PRODUCTO)).thenReturn(List.of(
                Lote.builder().id(UUID.randomUUID()).idProducto(ID_PRODUCTO).cantidad(4).build(),
                Lote.builder().id(UUID.randomUUID()).idProducto(ID_PRODUCTO).cantidad(0).build(),
                Lote.builder().id(UUID.randomUUID()).idProducto(ID_PRODUCTO).cantidad(9).build()));

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> productoService.delete(ID_PRODUCTO));

        assertTrue(ex.getMessage().contains("13 unidades en 2 lotes activos"), ex.getMessage());
        verify(productoRepository, never()).save(any());
        verify(loteRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("borrar un producto sin fila de stock no falla")
    void deleteSinStockNoFalla() {
        when(productoRepository.findByIdAndDeletedAtIsNull(ID_PRODUCTO)).thenReturn(productoConCategoria());
        when(stockRepository.findByIdProductoAndDeletedAtIsNull(ID_PRODUCTO)).thenReturn(Optional.empty());

        productoService.delete(ID_PRODUCTO);

        verify(stockRepository, never()).save(any());
    }

    @Test
    @DisplayName("una página resuelve cada categoría una sola vez, no una por fila")
    void laPaginaNoConsultaLaCategoriaPorFila() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<Producto> pagina = new PageImpl<>(
                List.of(productoConCategoria(), productoConCategoria(), productoConCategoria()),
                pageable, 3);
        when(productoRepository.findAllPaginated(pageable)).thenReturn(pagina);
        when(categoriasApi.getById(ID_CATEGORIA)).thenReturn(
                CategoriaResponse.builder().id(ID_CATEGORIA).nombre("Bebidas").build());

        Page<ProductoResponse> response = productoService.getAll(pageable);

        assertEquals(3, response.getContent().size());
        verify(categoriasApi, times(1)).getById(ID_CATEGORIA);
    }

    @Test
    @DisplayName("una categoría borrada tampoco se repregunta por cada fila")
    void laCategoriaInexistenteSeCacheaIgual() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<Producto> pagina = new PageImpl<>(
                List.of(productoConCategoria(), productoConCategoria()), pageable, 2);
        when(productoRepository.findAllPaginated(pageable)).thenReturn(pagina);
        when(categoriasApi.getById(ID_CATEGORIA)).thenThrow(new ResourceNotFoundException("Categoría no encontrada"));

        Page<ProductoResponse> response = productoService.getAll(pageable);

        assertNull(response.getContent().get(0).getCategoria());
        verify(categoriasApi, times(1)).getById(ID_CATEGORIA);
    }

    private Producto productoConCategoria() {
        return Producto.builder()
                .id(ID_PRODUCTO)
                .nombre("Coca Cola 2.25L")
                .barcode("7790001000056")
                .precio(3200f)
                .idCategoria(ID_CATEGORIA)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private ProductoRequest requestCompleto() {
        return ProductoRequest.builder()
                .nombre("Coca Cola 2.25L")
                .barcode("7790001000056")
                .precio(3200f)
                .build();
    }
}
