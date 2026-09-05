package com.SolucionesInformaticasBA.minimarket.modules.ventas.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.SolucionesInformaticasBA.minimarket.modules.caja.api.CajaApi;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.api.InventarioApi;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.repository.LoteRepository;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.repository.MovimientoStockRepository;
import com.SolucionesInformaticasBA.minimarket.modules.productos.api.ProductosApi;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.api.UsuarioApi;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.api.dto.VentaResponse;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.entity.DetalleVenta;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.entity.Venta;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.repository.DetalleVentaRepository;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.repository.VentaRepository;

@ExtendWith(MockitoExtension.class)
class VentaServiceListadosTest {

    @Mock private VentaRepository ventaRepository;
    @Mock private DetalleVentaRepository detalleVentaRepository;
    @Mock private UsuarioApi usuarioApi;
    @Mock private ProductosApi productosApi;
    @Mock private InventarioApi inventarioApi;
    @Mock private LoteRepository loteRepository;
    @Mock private MovimientoStockRepository movimientoStockRepository;
    @Mock private CajaApi cajaApi;

    @InjectMocks private VentaService ventaService;

    private static final UUID ID_VENTA = UUID.randomUUID();

    @Test
    @DisplayName("el listado consulta paginado y no trae la tabla entera para filtrar en memoria")
    void getAllNoTraeLaTablaEntera() {
        Pageable pageable = PageRequest.of(0, 20);
        when(ventaRepository.findAllByDeletedAtIsNull(pageable))
                .thenReturn(new PageImpl<>(List.of(venta()), pageable, 137));
        when(detalleVentaRepository.findByIdVentaInAndDeletedAtIsNull(List.of(ID_VENTA)))
                .thenReturn(List.of(detalle()));

        Page<VentaResponse> pagina = ventaService.getAll(pageable);

        assertEquals(1, pagina.getContent().size());
        // El total viene de la consulta, no de contar lo que se trajo a memoria.
        assertEquals(137, pagina.getTotalElements());
        verify(ventaRepository, never()).findAll();
    }

    @Test
    @DisplayName("los detalles de la página se traen en una sola consulta")
    void losDetallesSeTraenEnUnaConsulta() {
        Pageable pageable = PageRequest.of(0, 20);
        Venta otra = venta();
        otra.setId(UUID.randomUUID());
        when(ventaRepository.findAllByDeletedAtIsNull(pageable))
                .thenReturn(new PageImpl<>(List.of(venta(), otra), pageable, 2));
        when(detalleVentaRepository.findByIdVentaInAndDeletedAtIsNull(any())).thenReturn(List.of(detalle()));

        ventaService.getAll(pageable);

        verify(detalleVentaRepository).findByIdVentaInAndDeletedAtIsNull(any());
        verify(detalleVentaRepository, never()).findByIdVentaAndDeletedAtIsNull(any());
    }

    private Venta venta() {
        return Venta.builder()
                .id(ID_VENTA)
                .idUsuario(UUID.randomUUID())
                .total(1500f)
                .cobrada(false)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private DetalleVenta detalle() {
        return DetalleVenta.builder()
                .id(UUID.randomUUID())
                .idVenta(ID_VENTA)
                .nombreProducto("Leche")
                .cantidad(1)
                .precioUnitario(1500f)
                .build();
    }
}
