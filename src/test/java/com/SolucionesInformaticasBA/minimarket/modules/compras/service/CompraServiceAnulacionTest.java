package com.SolucionesInformaticasBA.minimarket.modules.compras.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.SolucionesInformaticasBA.minimarket.modules.caja.api.CajaApi;
import com.SolucionesInformaticasBA.minimarket.modules.compras.entity.Compra;
import com.SolucionesInformaticasBA.minimarket.modules.compras.repository.CompraRepository;
import com.SolucionesInformaticasBA.minimarket.modules.compras.repository.DetalleCompraRepository;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.api.InventarioApi;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.entity.MovimientoStock;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.repository.LoteRepository;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.repository.MovimientoStockRepository;
import com.SolucionesInformaticasBA.minimarket.modules.productos.api.ProductosApi;
import com.SolucionesInformaticasBA.minimarket.modules.productos.repository.ProductoRepository;
import com.SolucionesInformaticasBA.minimarket.modules.proveedores.api.ProveedoresApi;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.api.UsuarioApi;
import com.SolucionesInformaticasBA.minimarket.shared.exeption.BadRequestException;
import com.SolucionesInformaticasBA.minimarket.shared.exeption.UnauthorizedException;

/**
 * Quién anula una compra queda escrito en el movimiento de reversa de stock y en el de caja:
 * son las dos tablas con las que después se audita quién tocó qué, así que esa identidad tiene
 * que salir del JWT y no de un parámetro que manda el cliente.
 */
@ExtendWith(MockitoExtension.class)
class CompraServiceAnulacionTest {

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

    private static final UUID ID_COMPRA = UUID.randomUUID();
    private static final UUID ID_SESION = UUID.randomUUID();
    private static final UUID ID_DEL_JWT = UUID.randomUUID();

    @BeforeEach
    void autenticar() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(ID_DEL_JWT.toString(), null));
    }

    @AfterEach
    void limpiarContexto() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("la entrada de caja de la reversa se firma con el usuario del JWT")
    void laReversaDeCajaSeFirmaConElUsuarioDelJwt() {
        when(compraRepository.findByIdAndDeletedAtIsNull(ID_COMPRA))
                .thenReturn(Optional.of(compraPagadaPorCaja()));
        when(cajaApi.getIdSesionActiva()).thenReturn(ID_SESION);
        when(movimientoStockRepository.findByIdReferenciaAndTipoAndDeletedAtIsNull(any(), any()))
                .thenReturn(List.of());
        when(detalleCompraRepository.findByIdCompraAndDeletedAtIsNull(ID_COMPRA)).thenReturn(List.of());

        compraService.delete(ID_COMPRA);

        ArgumentCaptor<UUID> usuario = ArgumentCaptor.forClass(UUID.class);
        verify(cajaApi).registrarEntradaAutomatica(
                eq(ID_SESION), usuario.capture(), anyFloat(), eq("REVERSA"), eq(ID_COMPRA));
        assertEquals(ID_DEL_JWT, usuario.getValue());
    }

    @Test
    @DisplayName("sin sesión de caja abierta no se puede anular una compra pagada por caja")
    void sinTurnoAbiertoNoSeAnula() {
        when(compraRepository.findByIdAndDeletedAtIsNull(ID_COMPRA))
                .thenReturn(Optional.of(compraPagadaPorCaja()));
        when(cajaApi.getIdSesionActiva()).thenThrow(new BadRequestException("No hay sesión abierta"));

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> compraService.delete(ID_COMPRA));

        assertEquals("No se puede anular: la compra se pagó por caja y no hay un turno abierto "
                + "donde devolver la plata", ex.getMessage());
        verify(compraRepository, never()).save(any());
    }

    @Test
    @DisplayName("una compra que no se pagó por caja se anula sin tocar la caja")
    void compraSinCajaSeAnulaYSeDaDeBaja() {
        Compra compra = compraPagadaPorCaja();
        compra.setIdSesion(null);
        when(compraRepository.findByIdAndDeletedAtIsNull(ID_COMPRA)).thenReturn(Optional.of(compra));
        when(movimientoStockRepository.findByIdReferenciaAndTipoAndDeletedAtIsNull(any(), any()))
                .thenReturn(List.of());
        when(detalleCompraRepository.findByIdCompraAndDeletedAtIsNull(ID_COMPRA)).thenReturn(List.of());

        compraService.delete(ID_COMPRA);

        ArgumentCaptor<Compra> captor = ArgumentCaptor.forClass(Compra.class);
        verify(compraRepository).save(captor.capture());
        assertNotNull(captor.getValue().getDeletedAt());
        verify(cajaApi, never()).registrarEntradaAutomatica(any(), any(), anyFloat(), any(), any());
    }

    @Test
    @DisplayName("sin token no se anula: la identidad no tiene de dónde salir")
    void sinTokenNoSeAnula() {
        SecurityContextHolder.clearContext();
        when(compraRepository.findByIdAndDeletedAtIsNull(ID_COMPRA))
                .thenReturn(Optional.of(compraPagadaPorCaja()));

        assertThrows(UnauthorizedException.class, () -> compraService.delete(ID_COMPRA));

        verify(compraRepository, never()).save(any());
    }

    @Test
    @DisplayName("si la mercadería ya se vendió, el error habla de la anulación y no del stock")
    void mercaderiaYaVendidaExplicaLaAnulacion() {
        Compra compra = compraPagadaPorCaja();
        compra.setIdSesion(null);
        UUID idProducto = UUID.randomUUID();
        when(compraRepository.findByIdAndDeletedAtIsNull(ID_COMPRA)).thenReturn(Optional.of(compra));
        when(movimientoStockRepository.findByIdReferenciaAndTipoAndDeletedAtIsNull(any(), any()))
                .thenReturn(List.of(MovimientoStock.builder()
                        .idProducto(idProducto).cantidad(10).build()));
        // El producto no maneja lotes, así que la reversa pasa por disminuir, que rechaza dejar
        // el stock en negativo con un mensaje pensado para una venta, no para una anulación.
        when(inventarioApi.disminuir(any()))
                .thenThrow(new BadRequestException("Stock insuficiente. Disponible: 3, solicitado: 10"));
        when(productosApi.getNombresPorId(List.of(idProducto)))
                .thenReturn(Map.of(idProducto, "Leche entera"));

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> compraService.delete(ID_COMPRA));

        assertEquals("No se puede anular: ya se vendió parte de lo que ingresó esta compra "
                + "(Leche entera). Stock insuficiente. Disponible: 3, solicitado: 10",
                ex.getMessage());
        verify(compraRepository, never()).save(any());
    }

    private Compra compraPagadaPorCaja() {
        return Compra.builder()
                .id(ID_COMPRA)
                .idUsuario(UUID.randomUUID())
                .total(1500f)
                .idSesion(ID_SESION)
                .build();
    }
}
