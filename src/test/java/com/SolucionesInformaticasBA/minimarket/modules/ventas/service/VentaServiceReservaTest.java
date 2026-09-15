package com.SolucionesInformaticasBA.minimarket.modules.ventas.service;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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
import com.SolucionesInformaticasBA.minimarket.modules.productos.api.ProductosApi;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.api.UsuarioApi;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.entity.Venta;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.repository.DetalleVentaRepository;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.repository.VentaRepository;

/**
 * La anulación por reserva vencida la dispara un job, no una persona: no hay JWT del que sacar
 * la identidad, así que la anulación va sin usuario, y tiene que ser idempotente porque entre
 * que el barrido eligió la venta y llega acá puede haberse cobrado.
 *
 * <p>Lo que la anulación hace por dentro se prueba en {@link AnuladorVentasTest}: acá solo
 * interesa <b>cuándo</b> el barrido decide anular y cuándo se abstiene.
 */
@ExtendWith(MockitoExtension.class)
class VentaServiceReservaTest {

    @Mock private VentaRepository ventaRepository;
    @Mock private DetalleVentaRepository detalleVentaRepository;
    @Mock private UsuarioApi usuarioApi;
    @Mock private ProductosApi productosApi;
    @Mock private CajaApi cajaApi;
    @Mock private DescontadorStock descontadorStock;
    @Mock private AnuladorVentas anuladorVentas;

    @InjectMocks private VentaService ventaService;

    private static final UUID ID_VENTA = UUID.randomUUID();

    @Test
    @DisplayName("anula la venta sin cobrar, y sin usuario: la dispara el sistema")
    void anulaSinUsuario() {
        Venta venta = venta(false);
        when(ventaRepository.findByIdAndDeletedAtIsNull(ID_VENTA)).thenReturn(Optional.of(venta));

        ventaService.anularPorReservaVencida(ID_VENTA);

        ArgumentCaptor<UUID> usuario = ArgumentCaptor.forClass(UUID.class);
        verify(anuladorVentas).anular(eq(venta), usuario.capture(), any(LocalDateTime.class));
        assertNull(usuario.getValue(), "la anula el sistema, no una persona");

        // El barrido no pasa por los permisos: no hay nadie a quien pedírselos.
        verify(anuladorVentas, never()).validarPermiso(any(), any(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    @DisplayName("una venta que se cobró entre medio no se toca")
    void noAnulaUnaVentaYaCobrada() {
        when(ventaRepository.findByIdAndDeletedAtIsNull(ID_VENTA)).thenReturn(Optional.of(venta(true)));

        ventaService.anularPorReservaVencida(ID_VENTA);

        verify(anuladorVentas, never()).anular(any(), any(), any());
    }

    @Test
    @DisplayName("una venta que ya no está tampoco rompe el barrido")
    void ventaInexistenteNoRompe() {
        when(ventaRepository.findByIdAndDeletedAtIsNull(ID_VENTA)).thenReturn(Optional.empty());

        ventaService.anularPorReservaVencida(ID_VENTA);

        verify(anuladorVentas, never()).anular(any(), any(), any());
    }

    private Venta venta(boolean cobrada) {
        return Venta.builder()
                .id(ID_VENTA)
                .idUsuario(UUID.randomUUID())
                .total(new BigDecimal("1500.00"))
                .cobrada(cobrada)
                .createdAt(LocalDateTime.now().minusHours(5))
                .build();
    }
}
