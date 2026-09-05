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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.SolucionesInformaticasBA.minimarket.modules.caja.api.dto.CorteRequest;
import com.SolucionesInformaticasBA.minimarket.modules.caja.api.dto.CorteResponse;
import com.SolucionesInformaticasBA.minimarket.modules.caja.api.dto.ResumenCajaResponse;
import com.SolucionesInformaticasBA.minimarket.modules.caja.entity.MovimientoCaja;
import com.SolucionesInformaticasBA.minimarket.modules.caja.entity.SesionCaja;
import com.SolucionesInformaticasBA.minimarket.modules.caja.enums.EstadoSesion;
import com.SolucionesInformaticasBA.minimarket.modules.caja.enums.TipoMovimientoCaja;
import com.SolucionesInformaticasBA.minimarket.modules.caja.repository.MovimientoCajaRepository;
import com.SolucionesInformaticasBA.minimarket.modules.caja.repository.SesionCajaRepository;
import com.SolucionesInformaticasBA.minimarket.shared.exeption.BadRequestException;

/**
 * El corte es un documento contable: la fecha con la que se archiva y el hecho de que se
 * escriba una sola vez son parte del dato, no detalles de presentación.
 */
@ExtendWith(MockitoExtension.class)
class CajaServiceCorteTest {

    @Mock private SesionCajaRepository sesionCajaRepository;
    @Mock private MovimientoCajaRepository movimientoCajaRepository;

    @InjectMocks private CajaService cajaService;

    private static final UUID ID_SESION = UUID.randomUUID();
    private static final UUID ID_USUARIO = UUID.randomUUID();

    @Test
    @DisplayName("el corte se archiva con la fecha del turno, no con la del día en que se cierra")
    void elCorteUsaLaFechaDelTurno() {
        // Turno que abrió anteayer a las 22:00: cerrarlo hoy lo fechaba hoy.
        LocalDateTime apertura = LocalDateTime.now().minusDays(2).withHour(22).withMinute(0);
        when(sesionCajaRepository.findAbiertaParaActualizar())
                .thenReturn(Optional.of(sesionAbierta(apertura)));
        when(movimientoCajaRepository.findByIdSesionAndDeletedAtIsNull(ID_SESION))
                .thenReturn(List.of(entrada(1000f), salida(200f)));
        when(sesionCajaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CorteResponse corte = cajaService.realizarCorte(ID_USUARIO, corteCon(2300f));

        assertEquals(apertura.toLocalDate(), corte.getResumen().getFecha());
        // 1500 de saldo inicial + 1000 de entradas - 200 de salidas
        assertEquals(2300f, corte.getSaldoEsperado());
        assertEquals(0f, corte.getDiferencia());
    }

    @Test
    @DisplayName("el corte lee el turno con el lock tomado y no por el camino sin bloqueo")
    void elCorteTomaElLock() {
        when(sesionCajaRepository.findAbiertaParaActualizar())
                .thenReturn(Optional.of(sesionAbierta(LocalDateTime.now())));
        when(movimientoCajaRepository.findByIdSesionAndDeletedAtIsNull(ID_SESION)).thenReturn(List.of());
        when(sesionCajaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        cajaService.realizarCorte(ID_USUARIO, corteCon(1500f));

        verify(sesionCajaRepository).findAbiertaParaActualizar();
        verify(sesionCajaRepository, never())
                .findTopByEstadoAndDeletedAtIsNullOrderByCreatedAtDesc(any());
    }

    @Test
    @DisplayName("el turno queda cerrado con su desglose congelado")
    void elCorteCongelaElDesglose() {
        when(sesionCajaRepository.findAbiertaParaActualizar())
                .thenReturn(Optional.of(sesionAbierta(LocalDateTime.now())));
        when(movimientoCajaRepository.findByIdSesionAndDeletedAtIsNull(ID_SESION))
                .thenReturn(List.of(entrada(1000f)));
        when(sesionCajaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        cajaService.realizarCorte(ID_USUARIO, corteCon(2400f));

        ArgumentCaptor<SesionCaja> captor = ArgumentCaptor.forClass(SesionCaja.class);
        verify(sesionCajaRepository).save(captor.capture());
        SesionCaja cerrada = captor.getValue();
        assertEquals(EstadoSesion.CERRADA, cerrada.getEstado());
        assertEquals(1000f, cerrada.getTotalVentas());
        assertEquals(1, cerrada.getCantidadVentas());
        assertEquals(-100f, cerrada.getDiferencia());
        assertEquals(ID_USUARIO, cerrada.getIdUsuarioCierre());
    }

    @Test
    @DisplayName("sin turno abierto no hay corte")
    void sinTurnoAbiertoNoHayCorte() {
        when(sesionCajaRepository.findAbiertaParaActualizar()).thenReturn(Optional.empty());

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> cajaService.realizarCorte(ID_USUARIO, corteCon(1000f)));

        assertEquals("No hay una sesión de caja abierta", ex.getMessage());
        verify(sesionCajaRepository, never()).save(any());
    }

    @Test
    @DisplayName("el resumen del turno abierto también se fecha con la apertura")
    void elResumenDelTurnoUsaLaFechaDeApertura() {
        LocalDateTime apertura = LocalDateTime.now().minusDays(1).withHour(23);
        when(sesionCajaRepository.findTopByEstadoAndDeletedAtIsNullOrderByCreatedAtDesc(EstadoSesion.ABIERTA))
                .thenReturn(Optional.of(sesionAbierta(apertura)));
        when(movimientoCajaRepository.findByIdSesionAndDeletedAtIsNull(ID_SESION)).thenReturn(List.of());

        ResumenCajaResponse resumen = cajaService.getResumenSesion();

        assertEquals(apertura.toLocalDate(), resumen.getFecha());
    }

    private SesionCaja sesionAbierta(LocalDateTime fechaApertura) {
        return SesionCaja.builder()
                .id(ID_SESION)
                .fechaApertura(fechaApertura)
                .saldoInicial(1500f)
                .idUsuarioApertura(UUID.randomUUID())
                .estado(EstadoSesion.ABIERTA)
                .build();
    }

    private MovimientoCaja entrada(float monto) {
        return MovimientoCaja.builder()
                .id(UUID.randomUUID()).idSesion(ID_SESION).tipo(TipoMovimientoCaja.ENTRADA)
                .monto(monto).origen("VENTA").idUsuario(ID_USUARIO).build();
    }

    private MovimientoCaja salida(float monto) {
        return MovimientoCaja.builder()
                .id(UUID.randomUUID()).idSesion(ID_SESION).tipo(TipoMovimientoCaja.SALIDA)
                .monto(monto).origen("COMPRA").idUsuario(ID_USUARIO).build();
    }

    private CorteRequest corteCon(float saldoReal) {
        CorteRequest request = new CorteRequest();
        request.setSaldoReal(saldoReal);
        return request;
    }
}
