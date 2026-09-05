package com.SolucionesInformaticasBA.minimarket.modules.ventas.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.SolucionesInformaticasBA.minimarket.modules.caja.api.CajaApi;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.api.InventarioApi;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.repository.LoteRepository;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.repository.MovimientoStockRepository;
import com.SolucionesInformaticasBA.minimarket.modules.productos.api.ProductosApi;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.api.UsuarioApi;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.api.dto.CobrarVentaRequest;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.api.dto.CobrarVentaResponse;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.entity.Venta;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.repository.DetalleVentaRepository;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.repository.VentaRepository;
import com.SolucionesInformaticasBA.minimarket.shared.exeption.BadRequestException;

/**
 * El monto recibido es la plata que el cliente pone sobre el mostrador: solo existe cuando se
 * cobra en efectivo. Con tarjeta o transferencia el cajero tenía que inventar un número para
 * que el cobro pasara, y ese número quedaba guardado como si fuera real.
 */
@ExtendWith(MockitoExtension.class)
class VentaServiceCobroTest {

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
    private static final UUID ID_USUARIO = UUID.randomUUID();
    private static final UUID ID_SESION = UUID.randomUUID();

    @Test
    @DisplayName("cobrar con tarjeta no exige monto recibido ni lo guarda")
    void cobroConTarjetaIgnoraElMontoRecibido() {
        prepararCobro();
        when(cajaApi.buscarSesionActiva()).thenReturn(Optional.of(ID_SESION));

        CobrarVentaResponse response = ventaService.cobrar(ID_VENTA, ID_USUARIO, pago("TARJETA", null));

        assertEquals(0f, response.getCambio());
        assertNull(response.getVenta().getMontoRecibido());
        // La tarjeta no toca el arqueo: no genera entrada de caja.
        verify(cajaApi, never()).registrarEntradaAutomatica(any(), any(), anyFloat(), any(), any());
    }

    @Test
    @DisplayName("un monto recibido en tarjeta se descarta en vez de guardarse")
    void elMontoRecibidoEnTarjetaSeDescarta() {
        prepararCobro();
        when(cajaApi.buscarSesionActiva()).thenReturn(Optional.empty());

        CobrarVentaResponse response =
            ventaService.cobrar(ID_VENTA, ID_USUARIO, pago("TRANSFERENCIA", 99999f));

        assertNull(response.getVenta().getMontoRecibido());
        assertEquals(0f, response.getCambio());
    }

    @Test
    @DisplayName("cobrar en efectivo sin monto recibido es 400 y no un 500 por el null")
    void cobroEnEfectivoSinMontoEsBadRequest() {
        when(ventaRepository.findByIdAndCobradaFalseAndDeletedAtIsNull(ID_VENTA))
                .thenReturn(Optional.of(venta()));

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> ventaService.cobrar(ID_VENTA, ID_USUARIO, pago("EFECTIVO", null)));

        assertEquals("El monto recibido es obligatorio para cobrar en efectivo", ex.getMessage());
        verify(ventaRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("en efectivo se sigue exigiendo que alcance, y el vuelto se calcula")
    void cobroEnEfectivoValidaYDaVuelto() {
        when(ventaRepository.findByIdAndCobradaFalseAndDeletedAtIsNull(ID_VENTA))
                .thenReturn(Optional.of(venta()));

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> ventaService.cobrar(ID_VENTA, ID_USUARIO, pago("EFECTIVO", 1000f)));
        assertEquals("El monto recibido es menor al total de la venta", ex.getMessage());

        prepararCobro();
        when(cajaApi.getIdSesionActiva()).thenReturn(ID_SESION);

        CobrarVentaResponse response = ventaService.cobrar(ID_VENTA, ID_USUARIO, pago("EFECTIVO", 2000f));

        assertEquals(500f, response.getCambio());
        assertEquals(2000f, response.getVenta().getMontoRecibido());
        verify(cajaApi).registrarEntradaAutomatica(ID_SESION, ID_USUARIO, 1500f, "VENTA", ID_VENTA);
    }

    private void prepararCobro() {
        when(ventaRepository.findByIdAndCobradaFalseAndDeletedAtIsNull(ID_VENTA))
                .thenReturn(Optional.of(venta()));
        when(ventaRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(detalleVentaRepository.findByIdVentaAndDeletedAtIsNull(ID_VENTA)).thenReturn(List.of());
    }

    private Venta venta() {
        return Venta.builder()
                .id(ID_VENTA)
                .idUsuario(ID_USUARIO)
                .total(1500f)
                .cobrada(false)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private CobrarVentaRequest pago(String metodoPago, Float montoRecibido) {
        CobrarVentaRequest request = new CobrarVentaRequest();
        request.setMetodoPago(metodoPago);
        request.setMontoRecibido(montoRecibido);
        return request;
    }
}
