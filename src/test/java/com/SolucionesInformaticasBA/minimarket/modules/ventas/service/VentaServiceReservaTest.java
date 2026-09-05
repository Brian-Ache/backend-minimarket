package com.SolucionesInformaticasBA.minimarket.modules.ventas.service;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
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

import com.SolucionesInformaticasBA.minimarket.modules.caja.api.CajaApi;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.api.InventarioApi;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.api.dto.MovimientoStockRequest;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.entity.MovimientoStock;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.enums.TipoMovimiento;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.repository.LoteRepository;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.repository.MovimientoStockRepository;
import com.SolucionesInformaticasBA.minimarket.modules.productos.api.ProductosApi;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.api.UsuarioApi;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.entity.Venta;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.repository.DetalleVentaRepository;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.repository.VentaRepository;

/**
 * La anulación por reserva vencida la dispara un job, no una persona: no hay JWT del que sacar
 * la identidad, así que el movimiento de reversa queda sin usuario, y tiene que ser idempotente
 * porque entre que el barrido eligió la venta y llega acá puede haberse cobrado.
 */
@ExtendWith(MockitoExtension.class)
class VentaServiceReservaTest {

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
    private static final UUID ID_PRODUCTO = UUID.randomUUID();

    @Test
    @DisplayName("devuelve el stock y da de baja la venta, sin usuario en la reversa")
    void anulaYDevuelveElStockSinUsuario() {
        when(ventaRepository.findByIdAndDeletedAtIsNull(ID_VENTA)).thenReturn(Optional.of(venta(false)));
        when(movimientoStockRepository.findByIdReferenciaAndTipoAndDeletedAtIsNull(ID_VENTA, TipoMovimiento.VENTA))
                .thenReturn(List.of(MovimientoStock.builder()
                        .idProducto(ID_PRODUCTO).cantidad(-3).build()));
        when(detalleVentaRepository.findByIdVentaAndDeletedAtIsNull(ID_VENTA)).thenReturn(List.of());

        ventaService.anularPorReservaVencida(ID_VENTA);

        ArgumentCaptor<MovimientoStockRequest> reversa =
                ArgumentCaptor.forClass(MovimientoStockRequest.class);
        verify(inventarioApi).aumentar(reversa.capture());
        assertNull(reversa.getValue().getIdUsuario(), "la anula el sistema, no una persona");

        ArgumentCaptor<Venta> guardada = ArgumentCaptor.forClass(Venta.class);
        verify(ventaRepository).save(guardada.capture());
        assertNotNull(guardada.getValue().getDeletedAt());
    }

    @Test
    @DisplayName("una venta que se cobró entre medio no se toca")
    void noAnulaUnaVentaYaCobrada() {
        when(ventaRepository.findByIdAndDeletedAtIsNull(ID_VENTA)).thenReturn(Optional.of(venta(true)));

        ventaService.anularPorReservaVencida(ID_VENTA);

        verify(ventaRepository, never()).save(any());
        verify(inventarioApi, never()).aumentar(any());
    }

    @Test
    @DisplayName("una venta que ya no está tampoco rompe el barrido")
    void ventaInexistenteNoRompe() {
        when(ventaRepository.findByIdAndDeletedAtIsNull(ID_VENTA)).thenReturn(Optional.empty());

        ventaService.anularPorReservaVencida(ID_VENTA);

        verify(ventaRepository, never()).save(any());
    }

    private Venta venta(boolean cobrada) {
        return Venta.builder()
                .id(ID_VENTA)
                .idUsuario(UUID.randomUUID())
                .total(1500f)
                .cobrada(cobrada)
                .createdAt(LocalDateTime.now().minusHours(5))
                .build();
    }
}
