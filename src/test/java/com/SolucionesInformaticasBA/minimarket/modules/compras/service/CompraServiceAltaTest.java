package com.SolucionesInformaticasBA.minimarket.modules.compras.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.SolucionesInformaticasBA.minimarket.modules.caja.api.CajaApi;
import com.SolucionesInformaticasBA.minimarket.modules.compras.api.dto.CompraRequest;
import com.SolucionesInformaticasBA.minimarket.modules.compras.api.dto.DetalleCompraRequest;
import com.SolucionesInformaticasBA.minimarket.modules.compras.entity.Compra;
import com.SolucionesInformaticasBA.minimarket.modules.compras.repository.CompraRepository;
import com.SolucionesInformaticasBA.minimarket.modules.compras.repository.DetalleCompraRepository;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.api.InventarioApi;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.api.dto.LoteRequest;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.api.dto.LoteResponse;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.repository.LoteRepository;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.repository.MovimientoStockRepository;
import com.SolucionesInformaticasBA.minimarket.modules.productos.api.ProductosApi;
import com.SolucionesInformaticasBA.minimarket.modules.productos.entity.Producto;
import com.SolucionesInformaticasBA.minimarket.modules.productos.repository.ProductoRepository;
import com.SolucionesInformaticasBA.minimarket.modules.proveedores.api.ProveedoresApi;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.api.UsuarioApi;
import com.SolucionesInformaticasBA.minimarket.shared.exeption.BadRequestException;

@ExtendWith(MockitoExtension.class)
class CompraServiceAltaTest {

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

    private static final UUID ID_USUARIO = UUID.randomUUID();
    private static final UUID ID_PROVEEDOR = UUID.randomUUID();
    private static final UUID ID_PRODUCTO = UUID.randomUUID();

    @Test
    @DisplayName("el lote lo crea inventario, no la compra escribiendo la fila a mano")
    void elLoteSeCreaPorLaApiDeInventario() {
        prepararAlta(productoConLotes(true));
        when(proveedoresApi.existsById(ID_PROVEEDOR)).thenReturn(true);
        when(inventarioApi.crear(any(LoteRequest.class))).thenReturn(
                LoteResponse.builder().id(UUID.randomUUID()).idProducto(ID_PRODUCTO).build());

        compraService.crear(ID_USUARIO, compraCon(lineaConLote(LocalDate.now().plusMonths(6))));

        ArgumentCaptor<LoteRequest> captor = ArgumentCaptor.forClass(LoteRequest.class);
        verify(inventarioApi).crear(captor.capture());
        assertEquals(ID_PRODUCTO, captor.getValue().getIdProducto());
        assertEquals(3, captor.getValue().getCantidad());
        // La compra ya no escribe el lote por su cuenta: si lo hiciera, se saltearía las
        // validaciones que viven en inventario.
        verify(loteRepository, never()).save(any());
    }

    @Test
    @DisplayName("el tipo de comprobante se guarda normalizado")
    void elTipoDeComprobanteSeNormaliza() {
        prepararAlta(productoConLotes(false));
        when(proveedoresApi.existsById(ID_PROVEEDOR)).thenReturn(true);

        CompraRequest request = compraCon(linea());
        request.setTipoComprobante("  factura  ");
        request.setNroComprobante("  A-0001  ");

        compraService.crear(ID_USUARIO, request);

        ArgumentCaptor<Compra> captor = ArgumentCaptor.forClass(Compra.class);
        verify(compraRepository).save(captor.capture());
        assertEquals("FACTURA", captor.getValue().getTipoComprobante());
        assertEquals("A-0001", captor.getValue().getNroComprobante());
    }

    @Test
    @DisplayName("el filtro busca el tipo de comprobante con el mismo criterio con el que se guarda")
    void elFiltroNormalizaElTipo() {
        Pageable pageable = PageRequest.of(0, 20);
        when(compraRepository.findAllFiltered(any(), eq("FACTURA"), any(), any(), any()))
                .thenReturn(Page.empty(pageable));

        compraService.getAllFiltered(null, "factura", null, null, pageable);

        verify(compraRepository).findAllFiltered(null, "FACTURA", null, null, pageable);
    }

    @Test
    @DisplayName("el mismo proveedor no puede repetir número dentro del mismo tipo")
    void comprobanteRepetidoDelMismoProveedorEsBadRequest() {
        when(usuarioApi.existById(ID_USUARIO)).thenReturn(true);
        when(proveedoresApi.existsById(ID_PROVEEDOR)).thenReturn(true);
        when(compraRepository.existeComprobante(ID_PROVEEDOR, "FACTURA", "A-0001")).thenReturn(true);

        CompraRequest request = compraCon(linea());
        request.setTipoComprobante("factura");
        request.setNroComprobante("A-0001");

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> compraService.crear(ID_USUARIO, request));

        assertEquals("Ese proveedor ya tiene una compra registrada con el comprobante FACTURA A-0001",
                ex.getMessage());
        verify(compraRepository, never()).save(any());
    }

    @Test
    @DisplayName("un remito y una factura del mismo proveedor pueden llevar el mismo número")
    void mismoNumeroEnDistintoTipoSeAdmite() {
        // Cada tipo de comprobante lleva su propia numeración, así que no son el mismo
        // documento: la unicidad se chequea por proveedor y tipo, no solo por número.
        prepararAlta(productoConLotes(false));
        when(proveedoresApi.existsById(ID_PROVEEDOR)).thenReturn(true);
        when(compraRepository.existeComprobante(ID_PROVEEDOR, "REMITO", "A-0001")).thenReturn(false);

        CompraRequest request = compraCon(linea());
        request.setTipoComprobante("remito");
        request.setNroComprobante("A-0001");

        compraService.crear(ID_USUARIO, request);

        verify(compraRepository).existeComprobante(ID_PROVEEDOR, "REMITO", "A-0001");
    }

    @Test
    @DisplayName("una compra sin proveedor o sin número no tiene con qué compararse")
    void sinProveedorOSinNumeroNoSeValida() {
        prepararAlta(productoConLotes(false));

        CompraRequest sinProveedor = compraCon(linea());
        sinProveedor.setIdProveedor(null);
        sinProveedor.setNroComprobante("A-0001");

        compraService.crear(ID_USUARIO, sinProveedor);

        verify(compraRepository, never()).existeComprobante(any(), any(), any());
    }

    private void prepararAlta(Producto producto) {
        when(usuarioApi.existById(ID_USUARIO)).thenReturn(true);
        when(productoRepository.findByIdAndDeletedAtIsNull(ID_PRODUCTO)).thenReturn(producto);
        when(compraRepository.save(any())).thenAnswer(inv -> {
            Compra c = inv.getArgument(0);
            if (c.getId() == null) c.setId(UUID.randomUUID());
            return c;
        });
        when(compraRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private Producto productoConLotes(boolean manejaLotes) {
        return Producto.builder()
                .id(ID_PRODUCTO)
                .nombre("Leche")
                .barcode("779000123")
                .precio(1000f)
                .manejaLotes(manejaLotes)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private DetalleCompraRequest linea() {
        return DetalleCompraRequest.builder()
                .idProducto(ID_PRODUCTO)
                .precioUnitario(700f)
                .cantidad(3)
                .build();
    }

    private DetalleCompraRequest lineaConLote(LocalDate vencimiento) {
        DetalleCompraRequest d = linea();
        d.setFechaVencimiento(vencimiento);
        d.setNumeroLote("L-1");
        return d;
    }

    private CompraRequest compraCon(DetalleCompraRequest... lineas) {
        CompraRequest request = CompraRequest.builder()
                .detalle(List.of(lineas))
                .idProveedor(ID_PROVEEDOR)
                .build();
        return request;
    }
}
