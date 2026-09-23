package com.SolucionesInformaticasBA.minimarket.modules.productos.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
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

import com.SolucionesInformaticasBA.minimarket.modules.categorias.api.CategoriasApi;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.repository.LoteRepository;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.repository.MovimientoStockRepository;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.repository.StockRepository;
import com.SolucionesInformaticasBA.minimarket.modules.productos.api.dto.PrecioReferenciaResponse;
import com.SolucionesInformaticasBA.minimarket.modules.productos.entity.Producto;
import com.SolucionesInformaticasBA.minimarket.modules.productos.entity.ProductoProveedor;
import com.SolucionesInformaticasBA.minimarket.modules.productos.repository.ProductoProveedorRepository;
import com.SolucionesInformaticasBA.minimarket.modules.productos.repository.ProductoRepository;
import com.SolucionesInformaticasBA.minimarket.modules.proveedores.api.ProveedoresApi;
import com.SolucionesInformaticasBA.minimarket.modules.proveedores.api.dto.ProveedorResponse;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.api.UsuarioApi;
import com.SolucionesInformaticasBA.minimarket.shared.exeption.BadRequestException;
import com.SolucionesInformaticasBA.minimarket.shared.exeption.ResourceNotFoundException;

@ExtendWith(MockitoExtension.class)
class ProductoServicePrecioReferenciaTest {

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
    private static final UUID ID_PROVEEDOR = UUID.randomUUID();

    @Test
    @DisplayName("cargar un precio que no existía crea la referencia")
    void guardarCreaLaReferencia() {
        stubProductoYProveedorEnPie();
        when(productoProveedorRepository.findByIdProductoAndIdProveedorAndDeletedAtIsNull(
                ID_PRODUCTO, ID_PROVEEDOR)).thenReturn(Optional.empty());
        when(productoProveedorRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(proveedoresApi.getByIdIncluyendoBajas(ID_PROVEEDOR)).thenReturn(proveedor(null));

        PrecioReferenciaResponse response = productoService.guardarPrecioReferencia(
                ID_PRODUCTO, ID_PROVEEDOR, new BigDecimal("950.00"));

        ArgumentCaptor<ProductoProveedor> captor = ArgumentCaptor.forClass(ProductoProveedor.class);
        verify(productoProveedorRepository).save(captor.capture());
        assertEquals(ID_PRODUCTO, captor.getValue().getIdProducto());
        assertEquals(ID_PROVEEDOR, captor.getValue().getIdProveedor());
        assertEquals(new BigDecimal("950.00"), captor.getValue().getPrecioReferencia());
        assertEquals(new BigDecimal("950.00"), response.getPrecioReferencia());
    }

    @Test
    @DisplayName("cargar un precio que ya existía lo reescribe, no duplica la fila")
    void guardarEsUnUpsert() {
        stubProductoYProveedorEnPie();
        ProductoProveedor existente = referencia("800.00");
        when(productoProveedorRepository.findByIdProductoAndIdProveedorAndDeletedAtIsNull(
                ID_PRODUCTO, ID_PROVEEDOR)).thenReturn(Optional.of(existente));
        when(productoProveedorRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(proveedoresApi.getByIdIncluyendoBajas(ID_PROVEEDOR)).thenReturn(proveedor(null));

        productoService.guardarPrecioReferencia(ID_PRODUCTO, ID_PROVEEDOR, new BigDecimal("950.00"));

        ArgumentCaptor<ProductoProveedor> captor = ArgumentCaptor.forClass(ProductoProveedor.class);
        verify(productoProveedorRepository).save(captor.capture());
        // La misma fila, con el precio nuevo: el id no cambia.
        assertEquals(existente.getId(), captor.getValue().getId());
        assertEquals(new BigDecimal("950.00"), captor.getValue().getPrecioReferencia());
    }

    @Test
    @DisplayName("no se carga un precio de un proveedor dado de baja")
    void guardarConProveedorDeBajaEsBadRequest() {
        when(productoRepository.existsByIdAndDeletedAtIsNull(ID_PRODUCTO)).thenReturn(true);
        when(proveedoresApi.existsById(ID_PROVEEDOR)).thenReturn(false);

        assertThrows(BadRequestException.class, () -> productoService.guardarPrecioReferencia(
                ID_PRODUCTO, ID_PROVEEDOR, new BigDecimal("950.00")));

        verify(productoProveedorRepository, never()).save(any());
    }

    @Test
    @DisplayName("no se carga un precio sobre un producto que no existe")
    void guardarSobreProductoInexistenteEsNotFound() {
        when(productoRepository.existsByIdAndDeletedAtIsNull(ID_PRODUCTO)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> productoService.guardarPrecioReferencia(
                ID_PRODUCTO, ID_PROVEEDOR, new BigDecimal("950.00")));

        verify(productoProveedorRepository, never()).save(any());
    }

    @Test
    @DisplayName("borrar la referencia es baja lógica: libera el par sin perder la fila")
    void borrarEsBajaLogica() {
        ProductoProveedor existente = referencia("800.00");
        when(productoProveedorRepository.findByIdProductoAndIdProveedorAndDeletedAtIsNull(
                ID_PRODUCTO, ID_PROVEEDOR)).thenReturn(Optional.of(existente));

        productoService.borrarPrecioReferencia(ID_PRODUCTO, ID_PROVEEDOR);

        ArgumentCaptor<ProductoProveedor> captor = ArgumentCaptor.forClass(ProductoProveedor.class);
        verify(productoProveedorRepository).save(captor.capture());
        assertNotNull(captor.getValue().getDeletedAt());
        verify(productoProveedorRepository, never()).delete(any());
    }

    @Test
    @DisplayName("borrar una referencia que no existe es 404")
    void borrarInexistenteEsNotFound() {
        when(productoProveedorRepository.findByIdProductoAndIdProveedorAndDeletedAtIsNull(
                ID_PRODUCTO, ID_PROVEEDOR)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> productoService.borrarPrecioReferencia(ID_PRODUCTO, ID_PROVEEDOR));
    }

    @Test
    @DisplayName("un proveedor dado de baja conserva su precio y se muestra con deletedAt")
    void laBajaDelProveedorNoBorraElPrecio() {
        when(productoProveedorRepository
                .findByIdProductoAndDeletedAtIsNullOrderByPrecioReferenciaAsc(ID_PRODUCTO))
                .thenReturn(List.of(referencia("800.00")));
        when(proveedoresApi.getByIdIncluyendoBajas(ID_PROVEEDOR))
                .thenReturn(proveedor(LocalDateTime.now()));

        List<PrecioReferenciaResponse> referencias =
                productoService.getProveedoresDeProducto(ID_PRODUCTO);

        assertEquals(1, referencias.size());
        assertEquals(new BigDecimal("800.00"), referencias.get(0).getPrecioReferencia());
        assertNotNull(referencias.get(0).getProveedor().getDeletedAt());
    }

    @Test
    @DisplayName("borrar el producto también da de baja sus precios de referencia")
    void deleteDaDeBajaLasReferencias() {
        when(productoRepository.findByIdAndDeletedAtIsNull(ID_PRODUCTO)).thenReturn(
                Producto.builder().id(ID_PRODUCTO).nombre("Yerba").build());
        when(stockRepository.findByIdProductoParaActualizar(ID_PRODUCTO)).thenReturn(Optional.empty());
        when(loteRepository.findParaDescuentoFefo(ID_PRODUCTO)).thenReturn(List.of());
        when(productoProveedorRepository.findByIdProductoAndDeletedAtIsNull(ID_PRODUCTO))
                .thenReturn(List.of(referencia("800.00")));

        productoService.delete(ID_PRODUCTO);

        // Si sobreviven, el índice único las sigue contando y volver a cargar el mismo par
        // choca contra una fila de un producto que ya no existe.
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ProductoProveedor>> captor = ArgumentCaptor.forClass(List.class);
        verify(productoProveedorRepository).saveAll(captor.capture());
        assertEquals(1, captor.getValue().size());
        assertNotNull(captor.getValue().get(0).getDeletedAt());
    }

    // Helpers

    private void stubProductoYProveedorEnPie() {
        when(productoRepository.existsByIdAndDeletedAtIsNull(ID_PRODUCTO)).thenReturn(true);
        when(proveedoresApi.existsById(ID_PROVEEDOR)).thenReturn(true);
    }

    private ProductoProveedor referencia(String precio) {
        return ProductoProveedor.builder()
                .id(UUID.randomUUID())
                .idProducto(ID_PRODUCTO)
                .idProveedor(ID_PROVEEDOR)
                .precioReferencia(new BigDecimal(precio))
                .build();
    }

    private ProveedorResponse proveedor(LocalDateTime deletedAt) {
        return ProveedorResponse.builder()
                .id(ID_PROVEEDOR)
                .nombre("Distribuidora Sur")
                .deletedAt(deletedAt)
                .build();
    }
}
