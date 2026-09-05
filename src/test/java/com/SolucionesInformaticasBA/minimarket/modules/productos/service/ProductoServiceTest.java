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

import java.time.LocalDate;
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
import com.SolucionesInformaticasBA.minimarket.modules.inventario.entity.MovimientoStock;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.entity.Stock;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.enums.TipoMovimiento;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.repository.LoteRepository;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.repository.MovimientoStockRepository;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.repository.StockRepository;
import com.SolucionesInformaticasBA.minimarket.modules.productos.api.dto.LoteInicialRequest;
import com.SolucionesInformaticasBA.minimarket.modules.productos.api.dto.ProductoRequest;
import com.SolucionesInformaticasBA.minimarket.modules.productos.api.dto.ProductoResponse;
import com.SolucionesInformaticasBA.minimarket.modules.productos.entity.Producto;
import com.SolucionesInformaticasBA.minimarket.modules.productos.repository.ProductoProveedorRepository;
import com.SolucionesInformaticasBA.minimarket.modules.productos.repository.ProductoRepository;
import com.SolucionesInformaticasBA.minimarket.modules.proveedores.api.ProveedoresApi;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.api.UsuarioApi;
import com.SolucionesInformaticasBA.minimarket.shared.exeption.BadRequestException;
import com.SolucionesInformaticasBA.minimarket.shared.exeption.ResourceNotFoundException;

@ExtendWith(MockitoExtension.class)
class ProductoServiceTest {

    @Mock private ProductoRepository productoRepository;
    @Mock private ProductoProveedorRepository productoProveedorRepository;
    @Mock private StockRepository stockRepository;
    @Mock private LoteRepository loteRepository;
    @Mock private MovimientoStockRepository movimientoStockRepository;
    @Mock private UsuarioApi usuarioApi;
    @Mock private CategoriasApi categoriasApi;
    @Mock private ProveedoresApi proveedoresApi;

    @InjectMocks private ProductoService productoService;

    private static final UUID ID_PRODUCTO = UUID.randomUUID();
    private static final UUID ID_CATEGORIA = UUID.randomUUID();
    private static final UUID ID_USUARIO = UUID.randomUUID();
    private static final UUID ID_LOTE = UUID.randomUUID();
    private static final String BARCODE = "7790001000056";

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
    @DisplayName("el alta con cantidad inicial deja la fila de stock cargada y su movimiento")
    void crearConCantidadInicialCargaElStock() {
        stubAltaValida();

        productoService.crear(ID_USUARIO, request().cantidadInicial(12).build());

        ArgumentCaptor<Stock> stock = ArgumentCaptor.forClass(Stock.class);
        verify(stockRepository).save(stock.capture());
        assertEquals(12, stock.getValue().getCantidad());

        ArgumentCaptor<MovimientoStock> movimiento = ArgumentCaptor.forClass(MovimientoStock.class);
        verify(movimientoStockRepository).save(movimiento.capture());
        assertEquals(12, movimiento.getValue().getCantidad());
        assertEquals(TipoMovimiento.AJUSTE, movimiento.getValue().getTipo());
        assertEquals(ID_USUARIO, movimiento.getValue().getIdUsuario());
        assertNull(movimiento.getValue().getIdLote());
        verify(loteRepository, never()).save(any());
    }

    @Test
    @DisplayName("el alta sin cantidad inicial deja la fila de stock en 0 y ningún movimiento")
    void crearSinCantidadInicialNoDejaMovimiento() {
        stubAltaValida();

        productoService.crear(ID_USUARIO, requestCompleto());

        ArgumentCaptor<Stock> stock = ArgumentCaptor.forClass(Stock.class);
        verify(stockRepository).save(stock.capture());
        assertEquals(0, stock.getValue().getCantidad());
        verify(movimientoStockRepository, never()).save(any());
    }

    @Test
    @DisplayName("el alta de un producto con lotes crea el lote y no una fila de stock")
    void crearConLoteInicialNoCreaFilaDeStock() {
        stubAltaValida();
        when(loteRepository.save(any())).thenAnswer(inv -> {
            Lote l = inv.getArgument(0);
            l.setId(ID_LOTE);
            return l;
        });

        productoService.crear(ID_USUARIO, request()
                .manejaLotes(true)
                .cantidadInicial(24)
                .loteInicial(LoteInicialRequest.builder()
                        .numeroLote("L-882")
                        .fechaVencimiento(LocalDate.now().plusMonths(3))
                        .build())
                .build());

        ArgumentCaptor<Lote> lote = ArgumentCaptor.forClass(Lote.class);
        verify(loteRepository).save(lote.capture());
        assertEquals(24, lote.getValue().getCantidad());
        assertEquals("L-882", lote.getValue().getNumeroLote());

        ArgumentCaptor<MovimientoStock> movimiento = ArgumentCaptor.forClass(MovimientoStock.class);
        verify(movimientoStockRepository).save(movimiento.capture());
        assertEquals(ID_LOTE, movimiento.getValue().getIdLote());

        // La fila de stock de un producto con lotes no la lee nadie: sus existencias son la
        // suma de sus lotes.
        verify(stockRepository, never()).save(any());
    }

    @Test
    @DisplayName("un producto con lotes que nace vacío no crea ni stock ni lote")
    void crearConLotesEnCeroNoCreaNada() {
        stubAltaValida();

        productoService.crear(ID_USUARIO, request().manejaLotes(true).build());

        verify(stockRepository, never()).save(any());
        verify(loteRepository, never()).save(any());
        verify(movimientoStockRepository, never()).save(any());
    }

    @Test
    @DisplayName("un producto con lotes no nace con existencias sin decir de qué lote son")
    void crearConLotesYCantidadSinLoteInicialEsBadRequest() {
        when(usuarioApi.existById(ID_USUARIO)).thenReturn(true);
        when(productoRepository.findByBarcodeAndDeletedAtIsNull(BARCODE)).thenReturn(null);

        assertThrows(BadRequestException.class, () -> productoService.crear(ID_USUARIO,
                request().manejaLotes(true).cantidadInicial(24).build()));

        verify(productoRepository, never()).save(any());
    }

    @Test
    @DisplayName("un producto que no maneja lotes no acepta loteInicial")
    void crearSinLotesConLoteInicialEsBadRequest() {
        when(usuarioApi.existById(ID_USUARIO)).thenReturn(true);
        when(productoRepository.findByBarcodeAndDeletedAtIsNull(BARCODE)).thenReturn(null);

        assertThrows(BadRequestException.class, () -> productoService.crear(ID_USUARIO,
                request()
                        .cantidadInicial(5)
                        .loteInicial(LoteInicialRequest.builder()
                                .fechaVencimiento(LocalDate.now().plusMonths(3))
                                .build())
                        .build()));

        verify(productoRepository, never()).save(any());
    }

    @Test
    @DisplayName("no se puede pasar a manejar lotes un producto con existencias cargadas")
    void updateCambiandoManejaLotesConExistenciasEsBadRequest() {
        when(productoRepository.findByIdAndDeletedAtIsNull(ID_PRODUCTO)).thenReturn(productoConCategoria());
        when(stockRepository.findByIdProductoParaActualizar(ID_PRODUCTO)).thenReturn(Optional.of(
                Stock.builder().id(UUID.randomUUID()).idProducto(ID_PRODUCTO).cantidad(5).build()));

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> productoService.update(ID_PRODUCTO,
                        request().manejaLotes(true).build()));

        assertTrue(ex.getMessage().contains("5 unidades en stock"), ex.getMessage());
        verify(productoRepository, never()).save(any());
    }

    @Test
    @DisplayName("cambiar manejaLotes sí se puede con el producto en cero")
    void updateCambiandoManejaLotesSinExistencias() {
        when(productoRepository.findByIdAndDeletedAtIsNull(ID_PRODUCTO)).thenReturn(productoConCategoria());
        when(stockRepository.findByIdProductoParaActualizar(ID_PRODUCTO)).thenReturn(Optional.empty());
        when(loteRepository.findParaDescuentoFefo(ID_PRODUCTO)).thenReturn(List.of());
        when(productoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ProductoResponse response = productoService.update(ID_PRODUCTO,
                request().manejaLotes(true).build());

        assertTrue(response.isManejaLotes());
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
        when(stockRepository.findByIdProductoParaActualizar(ID_PRODUCTO)).thenReturn(Optional.of(stock));

        productoService.delete(ID_PRODUCTO);

        ArgumentCaptor<Stock> captor = ArgumentCaptor.forClass(Stock.class);
        verify(stockRepository).save(captor.capture());
        assertNotNull(captor.getValue().getDeletedAt());
    }

    @Test
    @DisplayName("borrar el producto también da de baja sus lotes")
    void deleteDaDeBajaLosLotes() {
        when(productoRepository.findByIdAndDeletedAtIsNull(ID_PRODUCTO)).thenReturn(productoConCategoria());
        when(stockRepository.findByIdProductoParaActualizar(ID_PRODUCTO)).thenReturn(Optional.empty());
        when(loteRepository.findParaDescuentoFefo(ID_PRODUCTO)).thenReturn(List.of(
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
        when(stockRepository.findByIdProductoParaActualizar(ID_PRODUCTO)).thenReturn(Optional.of(
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
        when(stockRepository.findByIdProductoParaActualizar(ID_PRODUCTO)).thenReturn(Optional.empty());
        when(loteRepository.findParaDescuentoFefo(ID_PRODUCTO)).thenReturn(List.of(
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
        when(stockRepository.findByIdProductoParaActualizar(ID_PRODUCTO)).thenReturn(Optional.empty());

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

    private ProductoRequest.ProductoRequestBuilder request() {
        return ProductoRequest.builder()
                .nombre("Coca Cola 2.25L")
                .barcode(BARCODE)
                .precio(3200f);
    }

    private ProductoRequest requestCompleto() {
        return request().build();
    }

    /** Un alta que pasa todas las validaciones previas, con el id del producto ya asignado. */
    private void stubAltaValida() {
        when(usuarioApi.existById(ID_USUARIO)).thenReturn(true);
        when(productoRepository.findByBarcodeAndDeletedAtIsNull(BARCODE)).thenReturn(null);
        when(productoRepository.save(any())).thenAnswer(inv -> {
            Producto guardado = inv.getArgument(0);
            guardado.setId(ID_PRODUCTO);
            return guardado;
        });
    }
}
