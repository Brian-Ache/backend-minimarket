package com.SolucionesInformaticasBA.minimarket.modules.ventas.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.SolucionesInformaticasBA.minimarket.modules.caja.api.CajaApi;
import com.SolucionesInformaticasBA.minimarket.modules.productos.api.ProductosApi;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.api.UsuarioApi;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.entity.Venta;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.repository.DetalleVentaRepository;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.repository.VentaRepository;
import com.SolucionesInformaticasBA.minimarket.shared.exeption.ForbiddenException;
import com.SolucionesInformaticasBA.minimarket.shared.exeption.ResourceNotFoundException;

/**
 * La anulación por el panel.
 *
 * <p>Lo que cambió en esta versión: una venta cobrada ahora se puede anular, y el permiso ya no
 * es "solo ADMIN" sino la regla de dueño y ventana, que se decide una sola vez y en el mismo
 * lugar por el que pasa la anulación que llega del front offline.
 */
@ExtendWith(MockitoExtension.class)
class VentaServiceAnulacionTest {

    @Mock private VentaRepository ventaRepository;
    @Mock private DetalleVentaRepository detalleVentaRepository;
    @Mock private UsuarioApi usuarioApi;
    @Mock private ProductosApi productosApi;
    @Mock private CajaApi cajaApi;
    @Mock private DescontadorStock descontadorStock;
    @Mock private AnuladorVentas anuladorVentas;

    @InjectMocks private VentaService ventaService;

    private static final UUID ID_VENTA = UUID.randomUUID();
    private static final UUID ID_DUENIO = UUID.randomUUID();

    @AfterEach
    void limpiarContexto() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("una venta cobrada ahora se anula: con la ventana, el 100% de lo anulable lo está")
    void laVentaCobradaSeAnula() {
        autenticarComo(ID_DUENIO, "ROLE_EMPLEADO");
        Venta venta = ventaCobrada();
        when(ventaRepository.findByIdAndDeletedAtIsNull(ID_VENTA)).thenReturn(Optional.of(venta));

        ventaService.delete(ID_VENTA);

        verify(anuladorVentas).anular(eq(venta), eq(ID_DUENIO), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("el permiso se pregunta con el dueño y el rol de quien está logueado")
    void elPermisoSaleDelJwt() {
        autenticarComo(ID_DUENIO, "ROLE_EMPLEADO");
        Venta venta = ventaCobrada();
        when(ventaRepository.findByIdAndDeletedAtIsNull(ID_VENTA)).thenReturn(Optional.of(venta));

        ventaService.delete(ID_VENTA);

        verify(anuladorVentas).validarPermiso(venta, ID_DUENIO, false);
    }

    @Test
    @DisplayName("un administrador pasa como admin")
    void elAdminPasaComoAdmin() {
        UUID admin = UUID.randomUUID();
        autenticarComo(admin, "ROLE_ADMIN");
        Venta venta = ventaCobrada();
        when(ventaRepository.findByIdAndDeletedAtIsNull(ID_VENTA)).thenReturn(Optional.of(venta));

        ventaService.delete(ID_VENTA);

        verify(anuladorVentas).validarPermiso(venta, admin, true);
    }

    @Test
    @DisplayName("si el permiso no da, no se anula nada")
    void sinPermisoNoSeAnula() {
        autenticarComo(UUID.randomUUID(), "ROLE_EMPLEADO");
        when(ventaRepository.findByIdAndDeletedAtIsNull(ID_VENTA)).thenReturn(Optional.of(ventaCobrada()));
        org.mockito.Mockito.doThrow(new ForbiddenException("no es tuya"))
            .when(anuladorVentas).validarPermiso(any(), any(), org.mockito.ArgumentMatchers.anyBoolean());

        assertThrows(ForbiddenException.class, () -> ventaService.delete(ID_VENTA));

        verify(anuladorVentas, never()).anular(any(), any(), any());
    }

    @Test
    @DisplayName("una venta que no existe es 404, no un 403 confuso")
    void ventaInexistente() {
        when(ventaRepository.findByIdAndDeletedAtIsNull(ID_VENTA)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> ventaService.delete(ID_VENTA));
    }

    @Test
    @DisplayName("la fecha de anulación del panel es ahora, no una que mande el cliente")
    void laFechaDelPanelEsAhora() {
        autenticarComo(ID_DUENIO, "ROLE_EMPLEADO");
        when(ventaRepository.findByIdAndDeletedAtIsNull(ID_VENTA)).thenReturn(Optional.of(ventaCobrada()));

        LocalDateTime antes = LocalDateTime.now();
        ventaService.delete(ID_VENTA);
        LocalDateTime despues = LocalDateTime.now();

        org.mockito.ArgumentCaptor<LocalDateTime> captor =
            org.mockito.ArgumentCaptor.forClass(LocalDateTime.class);
        verify(anuladorVentas).anular(any(), any(), captor.capture());
        assertEquals(true, !captor.getValue().isBefore(antes) && !captor.getValue().isAfter(despues));
    }

    private Venta ventaCobrada() {
        return Venta.builder()
            .id(ID_VENTA)
            .idUsuario(ID_DUENIO)
            .total(new BigDecimal("1500.00"))
            .cobrada(true)
            .metodoPago("EFECTIVO")
            .createdAt(LocalDateTime.now().minusDays(1))
            .build();
    }

    private void autenticarComo(UUID id, String rol) {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(id.toString(), null,
                List.of(new SimpleGrantedAuthority(rol))));
    }
}
