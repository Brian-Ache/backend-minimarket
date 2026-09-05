package com.SolucionesInformaticasBA.minimarket.modules.caja.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

import com.SolucionesInformaticasBA.minimarket.modules.caja.entity.MovimientoCaja;
import com.SolucionesInformaticasBA.minimarket.modules.caja.entity.SesionCaja;
import com.SolucionesInformaticasBA.minimarket.modules.caja.enums.EstadoSesion;
import com.SolucionesInformaticasBA.minimarket.modules.caja.enums.OrigenMovimientoCaja;
import com.SolucionesInformaticasBA.minimarket.modules.caja.repository.MovimientoCajaRepository;
import com.SolucionesInformaticasBA.minimarket.modules.caja.repository.SesionCajaRepository;
import com.SolucionesInformaticasBA.minimarket.shared.exeption.BadRequestException;
import com.SolucionesInformaticasBA.minimarket.shared.exeption.ResourceNotFoundException;

/**
 * Los movimientos que escribe el sistema al registrar un comprobante reciben el id del turno de
 * quien los origina. Aceptaban cualquiera, así que bastaba equivocarse para imputarle plata a un
 * turno cerrado y correrle el arqueo a un corte ya firmado.
 */
@ExtendWith(MockitoExtension.class)
class CajaServiceMovimientosAutomaticosTest {

    @Mock private SesionCajaRepository sesionCajaRepository;
    @Mock private MovimientoCajaRepository movimientoCajaRepository;

    @InjectMocks private CajaService cajaService;

    private static final UUID ID_SESION = UUID.randomUUID();
    private static final UUID ID_USUARIO = UUID.randomUUID();
    private static final UUID ID_VENTA = UUID.randomUUID();

    @Test
    @DisplayName("no se le pueden imputar movimientos a un turno cerrado")
    void turnoCerradoNoAceptaMovimientos() {
        when(sesionCajaRepository.findByIdAndDeletedAtIsNull(ID_SESION))
                .thenReturn(Optional.of(sesion(EstadoSesion.CERRADA)));

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> cajaService.registrarEntradaAutomatica(
                        ID_SESION, ID_USUARIO, 1500f, OrigenMovimientoCaja.VENTA, ID_VENTA));

        assertEquals("El turno de caja ya está cerrado: no se le pueden imputar movimientos nuevos",
                ex.getMessage());
        verify(movimientoCajaRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("un turno que no existe es 404 y no un movimiento colgado")
    void turnoInexistenteEsNotFound() {
        when(sesionCajaRepository.findByIdAndDeletedAtIsNull(ID_SESION)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> cajaService.registrarSalidaAutomatica(
                        ID_SESION, ID_USUARIO, 500f, OrigenMovimientoCaja.COMPRA, ID_VENTA));

        verify(movimientoCajaRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("sobre el turno abierto el movimiento se registra con su origen")
    void turnoAbiertoRegistraElMovimiento() {
        when(sesionCajaRepository.findByIdAndDeletedAtIsNull(ID_SESION))
                .thenReturn(Optional.of(sesion(EstadoSesion.ABIERTA)));
        when(movimientoCajaRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        cajaService.registrarEntradaAutomatica(
                ID_SESION, ID_USUARIO, 1500f, OrigenMovimientoCaja.VENTA, ID_VENTA);

        ArgumentCaptor<MovimientoCaja> captor = ArgumentCaptor.forClass(MovimientoCaja.class);
        verify(movimientoCajaRepository).saveAndFlush(captor.capture());
        assertEquals(OrigenMovimientoCaja.VENTA, captor.getValue().getOrigen());
        assertEquals(ID_VENTA, captor.getValue().getIdReferencia());
    }

    private SesionCaja sesion(EstadoSesion estado) {
        return SesionCaja.builder()
                .id(ID_SESION)
                .fechaApertura(LocalDateTime.now())
                .saldoInicial(1000f)
                .idUsuarioApertura(ID_USUARIO)
                .estado(estado)
                .build();
    }
}
