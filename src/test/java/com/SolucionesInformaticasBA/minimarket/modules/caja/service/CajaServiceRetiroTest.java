package com.SolucionesInformaticasBA.minimarket.modules.caja.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
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
import com.SolucionesInformaticasBA.minimarket.modules.caja.api.dto.CorteResponse;
import com.SolucionesInformaticasBA.minimarket.modules.caja.api.dto.ResumenCajaResponse;
import com.SolucionesInformaticasBA.minimarket.modules.caja.api.dto.SesionCajaResponse;
import com.SolucionesInformaticasBA.minimarket.modules.caja.entity.MovimientoCaja;
import com.SolucionesInformaticasBA.minimarket.modules.caja.entity.SesionCaja;
import com.SolucionesInformaticasBA.minimarket.modules.caja.enums.EstadoSesion;
import com.SolucionesInformaticasBA.minimarket.modules.caja.enums.OrigenMovimientoCaja;
import com.SolucionesInformaticasBA.minimarket.modules.caja.enums.TipoMovimientoCaja;
import com.SolucionesInformaticasBA.minimarket.modules.caja.repository.MovimientoCajaRepository;
import com.SolucionesInformaticasBA.minimarket.modules.caja.repository.SesionCajaRepository;
import com.SolucionesInformaticasBA.minimarket.shared.exeption.BadRequestException;

/**
 * El efectivo que queda en la caja al cerrar es el mismo que el turno siguiente declara al
 * abrir: si el día lo suma dos veces, el saldo esperado se infla turno a turno.
 */
@ExtendWith(MockitoExtension.class)
class CajaServiceRetiroTest {

    @Mock private SesionCajaRepository sesionCajaRepository;
    @Mock private MovimientoCajaRepository movimientoCajaRepository;

    @InjectMocks private CajaService cajaService;

    private static final UUID ID_SESION = UUID.randomUUID();
    private static final UUID ID_USUARIO = UUID.randomUUID();

    @Test
    @DisplayName("el retiro del cierre queda como salida del turno")
    void elRetiroSeRegistraComoSalida() {
        // Se hicieron 50.000 sobre 5.000 de apertura, se retiran 45.000 y quedan 10.000.
        when(sesionCajaRepository.findAbiertaParaActualizar())
                .thenReturn(Optional.of(sesionAbierta(5000f)));
        when(movimientoCajaRepository.findByIdSesionAndDeletedAtIsNull(ID_SESION))
                .thenReturn(List.of(entrada(50000f)));
        when(sesionCajaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CorteResponse corte = cajaService.realizarCorte(ID_USUARIO, corte(55000f, 45000f));

        assertEquals(45000f, corte.getMontoRetirado());
        assertEquals(10000f, corte.getSaldoDejado());

        ArgumentCaptor<MovimientoCaja> captor = ArgumentCaptor.forClass(MovimientoCaja.class);
        verify(movimientoCajaRepository).saveAndFlush(captor.capture());
        MovimientoCaja retiro = captor.getValue();
        assertEquals(TipoMovimientoCaja.SALIDA, retiro.getTipo());
        assertEquals(OrigenMovimientoCaja.RETIRO, retiro.getOrigen());
        assertEquals(45000f, retiro.getMonto());
        // El arqueo es el de antes de retirar: es lo que el cajero cuenta.
        assertEquals(55000f, corte.getSaldoEsperado());
    }

    @Test
    @DisplayName("cerrar sin retirar nada no genera movimiento")
    void sinRetiroNoHayMovimiento() {
        when(sesionCajaRepository.findAbiertaParaActualizar())
                .thenReturn(Optional.of(sesionAbierta(5000f)));
        when(movimientoCajaRepository.findByIdSesionAndDeletedAtIsNull(ID_SESION)).thenReturn(List.of());
        when(sesionCajaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CorteResponse corte = cajaService.realizarCorte(ID_USUARIO, corte(5000f, 0f));

        assertEquals(5000f, corte.getSaldoDejado());
        verify(movimientoCajaRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("no se puede retirar más de lo que hay contado")
    void noSePuedeRetirarDeMas() {
        when(sesionCajaRepository.findAbiertaParaActualizar())
                .thenReturn(Optional.of(sesionAbierta(5000f)));
        when(movimientoCajaRepository.findByIdSesionAndDeletedAtIsNull(ID_SESION)).thenReturn(List.of());

        assertThrows(BadRequestException.class,
                () -> cajaService.realizarCorte(ID_USUARIO, corte(5000f, 6000f)));

        verify(sesionCajaRepository, never()).save(any());
    }

    @Test
    @DisplayName("la apertura registra cuánto se apartó de lo que dejó el cierre anterior")
    void laAperturaRegistraLaDiferencia() {
        when(sesionCajaRepository.findTopByEstadoAndDeletedAtIsNullOrderByCreatedAtDesc(EstadoSesion.ABIERTA))
                .thenReturn(Optional.empty());
        SesionCaja anterior = sesionAbierta(5000f);
        anterior.setSaldoDejado(10000f);
        when(sesionCajaRepository.findTopByEstadoAndDeletedAtIsNullOrderByCreatedAtDesc(EstadoSesion.CERRADA))
                .thenReturn(Optional.of(anterior));
        when(sesionCajaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // El cierre anterior dejó 10.000 y al abrir se cuentan 9.800: faltan 200.
        SesionCajaResponse abierta = cajaService.abrirSesion(ID_USUARIO, apertura(9800f));

        assertEquals(-200f, abierta.getDiferenciaApertura());
    }

    @Test
    @DisplayName("sin cierre anterior con reparto registrado no hay contra qué comparar")
    void sinCierreAnteriorLaDiferenciaEsDesconocida() {
        when(sesionCajaRepository.findTopByEstadoAndDeletedAtIsNullOrderByCreatedAtDesc(EstadoSesion.ABIERTA))
                .thenReturn(Optional.empty());
        when(sesionCajaRepository.findTopByEstadoAndDeletedAtIsNullOrderByCreatedAtDesc(EstadoSesion.CERRADA))
                .thenReturn(Optional.empty());
        when(sesionCajaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SesionCajaResponse abierta = cajaService.abrirSesion(ID_USUARIO, apertura(5000f));

        assertNull(abierta.getDiferenciaApertura(), "null es 'no se sabe', no una diferencia de 0");
    }

    @Test
    @DisplayName("el día arranca con el primer turno y no vuelve a sumar lo que quedó en la caja")
    void elResumenDiarioNoSumaDosVecesLaMismaPlata() {
        LocalDate hoy = LocalDate.now();
        SesionCaja primero = sesionAbierta(5000f);
        primero.setFechaApertura(hoy.atTime(8, 0));
        SesionCaja segundo = sesionAbierta(10000f);
        segundo.setFechaApertura(hoy.atTime(16, 0));
        when(sesionCajaRepository.findByFechaAperturaGreaterThanEqualAndFechaAperturaLessThanAndDeletedAtIsNull(
                hoy.atStartOfDay(), hoy.plusDays(1).atStartOfDay()))
                .thenReturn(List.of(segundo, primero));
        // 70.000 de ventas y 70.000 retirados entre los dos cierres.
        when(movimientoCajaRepository.findEnRango(hoy.atStartOfDay(), hoy.plusDays(1).atStartOfDay()))
                .thenReturn(List.of(entrada(70000f), retiro(45000f), retiro(25000f)));

        ResumenCajaResponse resumen = cajaService.getResumenDiario(hoy);

        // Antes: 5.000 + 10.000 de aperturas. Ahora solo el primer turno.
        assertEquals(5000f, resumen.getSaldoInicial());
        // 5.000 + 70.000 - 70.000: lo que quedó físicamente en la caja al terminar el día.
        assertEquals(5000f, resumen.getSaldoEsperado());
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

    private MovimientoCaja retiro(float monto) {
        return MovimientoCaja.builder().id(UUID.randomUUID()).idSesion(ID_SESION)
                .tipo(TipoMovimientoCaja.SALIDA).monto(monto).origen(OrigenMovimientoCaja.RETIRO)
                .idUsuario(ID_USUARIO).build();
    }

    private CorteRequest corte(float saldoReal, float montoRetirado) {
        CorteRequest request = new CorteRequest();
        request.setSaldoReal(saldoReal);
        request.setMontoRetirado(montoRetirado);
        return request;
    }

    private AbrirSesionRequest apertura(float saldoInicial) {
        AbrirSesionRequest request = new AbrirSesionRequest();
        request.setSaldoInicial(saldoInicial);
        return request;
    }

}
