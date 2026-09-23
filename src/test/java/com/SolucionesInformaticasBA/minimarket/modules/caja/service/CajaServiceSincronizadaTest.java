package com.SolucionesInformaticasBA.minimarket.modules.caja.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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

import com.SolucionesInformaticasBA.minimarket.modules.caja.api.dto.AbrirSesionRequest;
import com.SolucionesInformaticasBA.minimarket.modules.caja.api.dto.CorteRequest;
import com.SolucionesInformaticasBA.minimarket.modules.caja.entity.MovimientoCaja;
import com.SolucionesInformaticasBA.minimarket.modules.caja.entity.SesionCaja;
import com.SolucionesInformaticasBA.minimarket.modules.caja.enums.EstadoSesion;
import com.SolucionesInformaticasBA.minimarket.modules.caja.enums.OrigenMovimientoCaja;
import com.SolucionesInformaticasBA.minimarket.modules.caja.repository.MovimientoCajaRepository;
import com.SolucionesInformaticasBA.minimarket.modules.caja.repository.SesionCajaRepository;
import com.SolucionesInformaticasBA.minimarket.shared.Uuid7;
import com.SolucionesInformaticasBA.minimarket.shared.exeption.BadRequestException;

/**
 * El turno de caja que ocurrió sin conexión: su uuid y sus dos fechas las trae el dispositivo.
 *
 * <p>Sin esto, un corte de internet que agarra un cambio de turno deja los tickets del turno
 * nuevo sin dónde colgarse: la sesión no existe en MySQL y la FK los rechaza.
 */
@ExtendWith(MockitoExtension.class)
class CajaServiceSincronizadaTest {

    @Mock private SesionCajaRepository sesionCajaRepository;
    @Mock private MovimientoCajaRepository movimientoCajaRepository;

    @InjectMocks private CajaService cajaService;

    private static final UUID ID_USUARIO = UUID.randomUUID();

    @Test
    @DisplayName("la apertura offline conserva el uuid y la fecha del dispositivo")
    void laAperturaConservaUuidYFecha() {
        UUID idTurno = Uuid7.nuevo();
        LocalDateTime anteayerALasOcho = LocalDateTime.now().minusDays(2).withHour(8).withMinute(0);
        when(sesionCajaRepository.findTopByEstadoAndDeletedAtIsNullOrderByCreatedAtDesc(EstadoSesion.ABIERTA))
            .thenReturn(Optional.empty());
        when(sesionCajaRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        cajaService.abrirSesionSincronizada(idTurno, ID_USUARIO, 15000f, anteayerALasOcho);

        ArgumentCaptor<SesionCaja> captor = ArgumentCaptor.forClass(SesionCaja.class);
        verify(sesionCajaRepository).save(captor.capture());
        assertEquals(idTurno, captor.getValue().getId());
        assertEquals(anteayerALasOcho, captor.getValue().getFechaApertura(),
            "el corte de ese turno se fecha con esto: puesto hoy, el arqueo sale corrido");
        assertEquals(EstadoSesion.ABIERTA, captor.getValue().getEstado());
    }

    @Test
    @DisplayName("la apertura online estrena un uuid v7, como las ventas")
    void laAperturaOnlineEstrenaUuidV7() {
        when(sesionCajaRepository.findTopByEstadoAndDeletedAtIsNullOrderByCreatedAtDesc(EstadoSesion.ABIERTA))
            .thenReturn(Optional.empty());
        when(sesionCajaRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        AbrirSesionRequest request = new AbrirSesionRequest();
        request.setSaldoInicial(15000f);
        cajaService.abrirSesion(ID_USUARIO, request);

        ArgumentCaptor<SesionCaja> captor = ArgumentCaptor.forClass(SesionCaja.class);
        verify(sesionCajaRepository).save(captor.capture());
        // Ahora que la tabla recibe ids del front, el backend genera del mismo formato: un v4
        // como PK fragmentaría el índice igual.
        assertTrue(Uuid7.esV7(captor.getValue().getId()));
    }

    @Test
    @DisplayName("el corte offline cierra ese turno y con la fecha del dispositivo")
    void elCorteUsaLaFechaDelDispositivo() {
        UUID idTurno = Uuid7.nuevo();
        LocalDateTime anteayerALasCatorce = LocalDateTime.now().minusDays(2).withHour(14).withMinute(0);
        SesionCaja abierta = sesionAbierta(idTurno);
        when(sesionCajaRepository.findAbiertaParaActualizar()).thenReturn(Optional.of(abierta));
        when(movimientoCajaRepository.findByIdSesionAndDeletedAtIsNull(idTurno)).thenReturn(List.of());
        when(sesionCajaRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        cajaService.realizarCorteSincronizado(idTurno, ID_USUARIO, corte(15000f, 0f),
            anteayerALasCatorce);

        assertEquals(anteayerALasCatorce, abierta.getFechaCierre());
        assertEquals(EstadoSesion.CERRADA, abierta.getEstado());
    }

    @Test
    @DisplayName("el retiro del corte se fecha con el cierre, no con el momento de sincronizar")
    void elRetiroSeFechaConElCierre() {
        UUID idTurno = Uuid7.nuevo();
        LocalDateTime anteayerALasCatorce = LocalDateTime.now().minusDays(2).withHour(14).withMinute(0);
        when(sesionCajaRepository.findAbiertaParaActualizar()).thenReturn(Optional.of(sesionAbierta(idTurno)));
        when(movimientoCajaRepository.findByIdSesionAndDeletedAtIsNull(idTurno)).thenReturn(List.of());
        when(sesionCajaRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(movimientoCajaRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));

        cajaService.realizarCorteSincronizado(idTurno, ID_USUARIO, corte(15000f, 10000f),
            anteayerALasCatorce);

        ArgumentCaptor<MovimientoCaja> captor = ArgumentCaptor.forClass(MovimientoCaja.class);
        verify(movimientoCajaRepository).saveAndFlush(captor.capture());
        assertEquals(OrigenMovimientoCaja.RETIRO, captor.getValue().getOrigen());
        // Fechado ahora, el retiro cae fuera de la vida de su propio turno y el resumen de caja
        // por fecha lo ve dos días después del corte que lo produjo.
        assertEquals(anteayerALasCatorce, captor.getValue().getCreatedAt());
    }

    @Test
    @DisplayName("no se cierra un turno que no es el que está abierto")
    void noCierraUnTurnoAjeno() {
        when(sesionCajaRepository.findAbiertaParaActualizar())
            .thenReturn(Optional.of(sesionAbierta(Uuid7.nuevo())));

        BadRequestException e = assertThrows(BadRequestException.class,
            () -> cajaService.realizarCorteSincronizado(Uuid7.nuevo(), ID_USUARIO,
                corte(15000f, 0f), LocalDateTime.now()));
        assertTrue(e.getMessage().contains("no es el que está abierto"));
    }

    @Test
    @DisplayName("con otro turno abierto la apertura se rechaza con un mensaje, no con el índice")
    void laAperturaQueChocaDaMensaje() {
        when(sesionCajaRepository.findTopByEstadoAndDeletedAtIsNullOrderByCreatedAtDesc(EstadoSesion.ABIERTA))
            .thenReturn(Optional.of(sesionAbierta(Uuid7.nuevo())));

        // uk_sesiones_una_abierta también lo impediría, pero una violación de índice sale como
        // un error opaco de la base y el front no puede distinguirla de un fallo cualquiera.
        BadRequestException e = assertThrows(BadRequestException.class,
            () -> cajaService.abrirSesionSincronizada(Uuid7.nuevo(), ID_USUARIO, 15000f,
                LocalDateTime.now()));
        assertTrue(e.getMessage().contains("Ya existe una sesión de caja abierta"));
    }

    private SesionCaja sesionAbierta(UUID id) {
        return SesionCaja.builder()
            .id(id)
            .fechaApertura(LocalDateTime.now().minusDays(2).withHour(8).withMinute(0))
            .saldoInicial(15000f)
            .estado(EstadoSesion.ABIERTA)
            .build();
    }

    private CorteRequest corte(float saldoReal, float montoRetirado) {
        CorteRequest request = new CorteRequest();
        request.setSaldoReal(saldoReal);
        request.setMontoRetirado(montoRetirado);
        request.setObservaciones("turno offline");
        return request;
    }
}
