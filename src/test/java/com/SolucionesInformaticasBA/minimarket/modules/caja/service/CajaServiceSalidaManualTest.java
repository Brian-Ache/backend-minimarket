package com.SolucionesInformaticasBA.minimarket.modules.caja.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.SolucionesInformaticasBA.minimarket.modules.caja.api.dto.MovimientoCajaRequest;
import com.SolucionesInformaticasBA.minimarket.modules.caja.entity.MovimientoCaja;
import com.SolucionesInformaticasBA.minimarket.modules.caja.entity.SesionCaja;
import com.SolucionesInformaticasBA.minimarket.modules.caja.enums.EstadoSesion;
import com.SolucionesInformaticasBA.minimarket.modules.caja.enums.OrigenMovimientoCaja;
import com.SolucionesInformaticasBA.minimarket.modules.caja.enums.TipoMovimientoCaja;
import com.SolucionesInformaticasBA.minimarket.modules.caja.repository.MovimientoCajaRepository;
import com.SolucionesInformaticasBA.minimarket.modules.caja.repository.SesionCajaRepository;
import com.SolucionesInformaticasBA.minimarket.shared.exeption.BadRequestException;

/** De la caja no puede salir plata que no está. */
@ExtendWith(MockitoExtension.class)
class CajaServiceSalidaManualTest {

    @Mock private SesionCajaRepository sesionCajaRepository;
    @Mock private MovimientoCajaRepository movimientoCajaRepository;

    @InjectMocks private CajaService cajaService;

    private static final UUID ID_SESION = UUID.randomUUID();
    private static final UUID ID_USUARIO = UUID.randomUUID();

    @Test
    @DisplayName("una salida manual no puede dejar la caja en negativo")
    void laSalidaManualNoDejaLaCajaEnNegativo() {
        when(sesionCajaRepository.findTopByEstadoAndDeletedAtIsNullOrderByCreatedAtDesc(EstadoSesion.ABIERTA))
                .thenReturn(Optional.of(sesionAbierta(3000f)));
        when(movimientoCajaRepository.findByIdSesionAndDeletedAtIsNull(ID_SESION)).thenReturn(List.of());

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> cajaService.registrarSalidaManual(ID_USUARIO, salidaDe(50000f)));

        assertEquals("No hay efectivo suficiente en la caja: el turno tiene 3000.0 y se intentan "
                + "retirar 50000.0", ex.getMessage());
        verify(movimientoCajaRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("una salida que entra en el saldo disponible se registra")
    void laSalidaQueEntraEnElSaldoSeRegistra() {
        when(sesionCajaRepository.findTopByEstadoAndDeletedAtIsNullOrderByCreatedAtDesc(EstadoSesion.ABIERTA))
                .thenReturn(Optional.of(sesionAbierta(3000f)));
        when(movimientoCajaRepository.findByIdSesionAndDeletedAtIsNull(ID_SESION))
                .thenReturn(List.of(entrada(2000f)));
        when(movimientoCajaRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        cajaService.registrarSalidaManual(ID_USUARIO, salidaDe(5000f));

        verify(movimientoCajaRepository).saveAndFlush(any());
    }

    private SesionCaja sesionAbierta(float saldoInicial) {
        return SesionCaja.builder()
                .id(ID_SESION)
                .fechaApertura(LocalDateTime.now())
                .saldoInicial(saldoInicial)
                .idUsuarioApertura(ID_USUARIO)
                .estado(EstadoSesion.ABIERTA)
                .build();
    }

    private MovimientoCaja entrada(float monto) {
        return MovimientoCaja.builder().id(UUID.randomUUID()).idSesion(ID_SESION)
                .tipo(TipoMovimientoCaja.ENTRADA).monto(monto).origen(OrigenMovimientoCaja.VENTA)
                .idUsuario(ID_USUARIO).build();
    }

    private MovimientoCajaRequest salidaDe(float monto) {
        MovimientoCajaRequest request = new MovimientoCajaRequest();
        request.setMonto(monto);
        request.setMotivo("prueba");
        return request;
    }
}
