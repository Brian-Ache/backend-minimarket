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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.SolucionesInformaticasBA.minimarket.modules.caja.api.dto.MovimientoCajaResponse;
import com.SolucionesInformaticasBA.minimarket.modules.caja.entity.MovimientoCaja;
import com.SolucionesInformaticasBA.minimarket.modules.caja.entity.SesionCaja;
import com.SolucionesInformaticasBA.minimarket.modules.caja.enums.EstadoSesion;
import com.SolucionesInformaticasBA.minimarket.modules.caja.enums.OrigenMovimientoCaja;
import com.SolucionesInformaticasBA.minimarket.modules.caja.enums.TipoMovimientoCaja;
import com.SolucionesInformaticasBA.minimarket.modules.caja.repository.MovimientoCajaRepository;
import com.SolucionesInformaticasBA.minimarket.modules.caja.repository.SesionCajaRepository;
import com.SolucionesInformaticasBA.minimarket.shared.exeption.BadRequestException;

@ExtendWith(MockitoExtension.class)
class CajaServiceMovimientosTest {

    @Mock private SesionCajaRepository sesionCajaRepository;
    @Mock private MovimientoCajaRepository movimientoCajaRepository;

    @InjectMocks private CajaService cajaService;

    private static final UUID ID_SESION = UUID.randomUUID();
    private final Pageable pageable = PageRequest.of(0, 20);

    @Test
    @DisplayName("sin fechas devuelve los movimientos del turno abierto, paginados")
    void sinFechasDevuelveElTurnoAbierto() {
        when(sesionCajaRepository.findTopByEstadoAndDeletedAtIsNullOrderByCreatedAtDesc(EstadoSesion.ABIERTA))
                .thenReturn(Optional.of(sesion()));
        when(movimientoCajaRepository.findByIdSesionAndDeletedAtIsNull(ID_SESION, pageable))
                .thenReturn(new PageImpl<>(List.of(movimiento()), pageable, 1));

        Page<MovimientoCajaResponse> pagina = cajaService.getMovimientos(null, null, pageable);

        assertEquals(1, pagina.getContent().size());
        verify(movimientoCajaRepository, never()).findEnRango(any(), any(), any());
    }

    @Test
    @DisplayName("con una sola fecha se avisa, en vez de barrer desde el año 2000")
    void unaSolaFechaEsBadRequest() {
        LocalDateTime hasta = LocalDateTime.now();

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> cajaService.getMovimientos(null, hasta, pageable));
        assertEquals("Para consultar por fechas hay que indicar 'desde' y 'hasta'; sin ninguna de "
                + "las dos se devuelven los movimientos del turno abierto", ex.getMessage());

        assertThrows(BadRequestException.class,
                () -> cajaService.getMovimientos(hasta, null, pageable));

        verify(movimientoCajaRepository, never()).findEnRango(any(), any(), any());
    }

    @Test
    @DisplayName("un rango invertido es 400 y no un listado vacío")
    void rangoInvertidoEsBadRequest() {
        LocalDateTime desde = LocalDateTime.now();

        assertThrows(BadRequestException.class,
                () -> cajaService.getMovimientos(desde, desde.minusDays(1), pageable));
        assertThrows(BadRequestException.class,
                () -> cajaService.getMovimientos(desde, desde, pageable));

        verify(movimientoCajaRepository, never()).findEnRango(any(), any(), any());
    }

    @Test
    @DisplayName("con las dos fechas consulta el rango paginado")
    void conRangoConsultaPaginado() {
        LocalDateTime desde = LocalDateTime.now().minusDays(1);
        LocalDateTime hasta = LocalDateTime.now();
        when(movimientoCajaRepository.findEnRango(desde, hasta, pageable))
                .thenReturn(new PageImpl<>(List.of(movimiento()), pageable, 348));

        Page<MovimientoCajaResponse> pagina = cajaService.getMovimientos(desde, hasta, pageable);

        assertEquals(348, pagina.getTotalElements());
        verify(movimientoCajaRepository, never()).findEnRango(any(), any());
    }

    private SesionCaja sesion() {
        return SesionCaja.builder()
                .id(ID_SESION)
                .fechaApertura(LocalDateTime.now())
                .saldoInicial(1000f)
                .idUsuarioApertura(UUID.randomUUID())
                .estado(EstadoSesion.ABIERTA)
                .build();
    }

    private MovimientoCaja movimiento() {
        return MovimientoCaja.builder()
                .id(UUID.randomUUID())
                .idSesion(ID_SESION)
                .tipo(TipoMovimientoCaja.ENTRADA)
                .monto(500f)
                .origen(OrigenMovimientoCaja.VENTA)
                .idUsuario(UUID.randomUUID())
                .build();
    }
}
