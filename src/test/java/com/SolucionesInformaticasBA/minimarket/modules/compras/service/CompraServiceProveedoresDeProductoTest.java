package com.SolucionesInformaticasBA.minimarket.modules.compras.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.SolucionesInformaticasBA.minimarket.modules.caja.api.CajaApi;
import com.SolucionesInformaticasBA.minimarket.modules.compras.api.dto.ProveedorDeProductoResponse;
import com.SolucionesInformaticasBA.minimarket.modules.compras.repository.CompraRepository;
import com.SolucionesInformaticasBA.minimarket.modules.compras.repository.DetalleCompraRepository;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.api.InventarioApi;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.repository.LoteRepository;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.repository.MovimientoStockRepository;
import com.SolucionesInformaticasBA.minimarket.modules.productos.api.ProductosApi;
import com.SolucionesInformaticasBA.minimarket.modules.productos.api.dto.PrecioReferenciaResponse;
import com.SolucionesInformaticasBA.minimarket.modules.productos.api.dto.ProductoResponse;
import com.SolucionesInformaticasBA.minimarket.modules.productos.repository.ProductoRepository;
import com.SolucionesInformaticasBA.minimarket.modules.proveedores.api.ProveedoresApi;
import com.SolucionesInformaticasBA.minimarket.modules.proveedores.api.dto.ProveedorResponse;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.api.UsuarioApi;
import com.SolucionesInformaticasBA.minimarket.shared.exeption.ResourceNotFoundException;

@ExtendWith(MockitoExtension.class)
class CompraServiceProveedoresDeProductoTest {

    @Mock private CompraRepository compraRepository;
    @Mock private DetalleCompraRepository detalleCompraRepository;
    @Mock private UsuarioApi usuarioApi;
    @Mock private ProductosApi productosApi;
    @Mock private ProductoRepository productoRepository;
    @Mock private InventarioApi inventarioApi;
    @Mock private LoteRepository loteRepository;
    @Mock private MovimientoStockRepository movimientoStockRepository;
    @Mock private ProveedoresApi proveedoresApi;
    @Mock private CajaApi cajaApi;

    @InjectMocks private CompraService compraService;

    private static final UUID ID_PRODUCTO = UUID.randomUUID();
    private static final UUID ID_SUR = UUID.randomUUID();
    private static final UUID ID_NORTE = UUID.randomUUID();

    @Test
    @DisplayName("cruza el precio de referencia con lo que realmente se pagó")
    void cruzaReferenciaConHistorial() {
        stubProducto();
        when(productosApi.getProveedoresDeProducto(ID_PRODUCTO))
                .thenReturn(List.of(referencia(ID_SUR, "Distribuidora Sur", "950.00")));
        when(detalleCompraRepository.ultimaCompraPorProveedor(ID_PRODUCTO))
                .thenReturn(List.<Object[]>of(ultimaCompra(ID_SUR, 910f)));

        List<ProveedorDeProductoResponse> proveedores =
                compraService.getProveedoresDeProducto(ID_PRODUCTO);

        assertEquals(1, proveedores.size());
        assertEquals(new BigDecimal("950.00"), proveedores.get(0).getPrecioReferencia());
        assertEquals(910f, proveedores.get(0).getUltimaCompra().getPrecioUnitario());
        assertEquals("0001-00012345", proveedores.get(0).getUltimaCompra().getNroComprobante());
    }

    @Test
    @DisplayName("un proveedor con precio cargado y sin compras sale con ultimaCompra en null")
    void referenciaSinCompras() {
        stubProducto();
        when(productosApi.getProveedoresDeProducto(ID_PRODUCTO))
                .thenReturn(List.of(referencia(ID_SUR, "Distribuidora Sur", "950.00")));
        when(detalleCompraRepository.ultimaCompraPorProveedor(ID_PRODUCTO)).thenReturn(List.<Object[]>of());

        List<ProveedorDeProductoResponse> proveedores =
                compraService.getProveedoresDeProducto(ID_PRODUCTO);

        assertNull(proveedores.get(0).getUltimaCompra());
        assertNotNull(proveedores.get(0).getPrecioReferencia());
    }

    @Test
    @DisplayName("a quien se le compró sin tener precio cargado también aparece, al final")
    void elHistorialSinReferenciaVaAlFinal() {
        stubProducto();
        when(productosApi.getProveedoresDeProducto(ID_PRODUCTO))
                .thenReturn(List.of(referencia(ID_SUR, "Distribuidora Sur", "950.00")));
        when(detalleCompraRepository.ultimaCompraPorProveedor(ID_PRODUCTO))
                .thenReturn(List.<Object[]>of(ultimaCompra(ID_NORTE, 880f)));
        when(proveedoresApi.getByIdIncluyendoBajas(ID_NORTE))
                .thenReturn(ProveedorResponse.builder().id(ID_NORTE).nombre("Mayorista Norte").build());

        List<ProveedorDeProductoResponse> proveedores =
                compraService.getProveedoresDeProducto(ID_PRODUCTO);

        assertEquals(2, proveedores.size());
        assertEquals(ID_SUR, proveedores.get(0).getProveedor().getId());
        assertEquals(ID_NORTE, proveedores.get(1).getProveedor().getId());
        // Nunca se le cargó un precio: solo se sabe lo que se le pagó.
        assertNull(proveedores.get(1).getPrecioReferencia());
        assertEquals(880f, proveedores.get(1).getUltimaCompra().getPrecioUnitario());
    }

    @Test
    @DisplayName("el proveedor del catálogo no se vuelve a resolver: ya viene con la referencia")
    void noRepreguntaElProveedorDelCatalogo() {
        stubProducto();
        when(productosApi.getProveedoresDeProducto(ID_PRODUCTO))
                .thenReturn(List.of(referencia(ID_SUR, "Distribuidora Sur", "950.00")));
        when(detalleCompraRepository.ultimaCompraPorProveedor(ID_PRODUCTO))
                .thenReturn(List.<Object[]>of(ultimaCompra(ID_SUR, 910f)));

        compraService.getProveedoresDeProducto(ID_PRODUCTO);

        // Sin stub de getByIdIncluyendoBajas: si el servicio lo llamara, con strict stubs el
        // mock devolvería null y el orden por nombre reventaría.
        assertEquals(1, compraService.getProveedoresDeProducto(ID_PRODUCTO).size());
    }

    @Test
    @DisplayName("preguntar por un producto que no existe es 404 y no una lista vacía")
    void productoInexistenteEsNotFound() {
        when(productosApi.getById(ID_PRODUCTO))
                .thenThrow(new ResourceNotFoundException("Producto no encontrado"));

        assertThrows(ResourceNotFoundException.class,
                () -> compraService.getProveedoresDeProducto(ID_PRODUCTO));
    }

    // Helpers

    private void stubProducto() {
        when(productosApi.getById(ID_PRODUCTO)).thenReturn(
                ProductoResponse.builder().id(ID_PRODUCTO).nombre("Yerba 1kg").build());
    }

    private PrecioReferenciaResponse referencia(UUID idProveedor, String nombre, String precio) {
        return PrecioReferenciaResponse.builder()
                .proveedor(ProveedorResponse.builder().id(idProveedor).nombre(nombre).build())
                .precioReferencia(new BigDecimal(precio))
                .actualizado(LocalDateTime.now())
                .build();
    }

    /** Una fila como la que devuelve la consulta del repositorio, en el mismo orden. */
    private Object[] ultimaCompra(UUID idProveedor, float precioUnitario) {
        return new Object[] {
            idProveedor,
            LocalDateTime.now().minusDays(3),
            "FACTURA",
            "0001-00012345",
            precioUnitario
        };
    }
}
