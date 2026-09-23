package com.SolucionesInformaticasBA.minimarket.modules.ventas.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.SolucionesInformaticasBA.minimarket.modules.ventas.api.dto.EventoSyncRequest;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.api.dto.ResultadoEventoSync;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.api.dto.SyncLoteRequest;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.api.dto.SyncLoteResponse;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.enums.CodigoErrorSync;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.enums.TipoEventoSync;
import com.SolucionesInformaticasBA.minimarket.shared.Uuid7;
import com.SolucionesInformaticasBA.minimarket.shared.exeption.BadRequestException;

/**
 * La orquestación del lote: orden, tope y un resultado por ítem.
 *
 * <p>Lo que aplica cada evento se prueba aparte, en {@link ProcesadorEventoSyncTest}. Acá el
 * procesador va mockeado a propósito: lo que importa es que un evento que falla no arrastre a
 * los demás y que el orden sea el del local, no el del array.
 */
@ExtendWith(MockitoExtension.class)
class SyncVentasServiceTest {

    @Mock private ProcesadorEventoSync procesador;

    @InjectMocks private SyncVentasService syncVentasService;

    private static final UUID ID_SYNC = UUID.randomUUID();

    @BeforeEach
    void configurarTope() {
        ReflectionTestUtils.setField(syncVentasService, "loteMaximo", 100);
    }

    @Test
    @DisplayName("un evento que falla no impide que entren los demás del lote")
    void unEventoQueFallaNoArrastraAlResto() {
        EventoSyncRequest bueno = evento(LocalDateTime.now().minusHours(2), 1);
        EventoSyncRequest malo = evento(LocalDateTime.now().minusHours(1), 2);

        when(procesador.procesar(eq(bueno), any(), any())).thenReturn(ResultadoEventoSync.ok(bueno));
        when(procesador.procesar(eq(malo), any(), any()))
            .thenThrow(new EventoSyncException(CodigoErrorSync.VENDEDOR_INEXISTENTE, "no existe"));

        SyncLoteResponse response = syncVentasService.sincronizar(ID_SYNC, lote(bueno, malo));

        assertEquals(2, response.getRecibidos());
        assertEquals("OK", response.getResultados().get(0).getEstado());
        assertEquals("ERROR", response.getResultados().get(1).getEstado());
        assertEquals(CodigoErrorSync.VENDEDOR_INEXISTENTE, response.getResultados().get(1).getCodigo());
    }

    @Test
    @DisplayName("los eventos se aplican en el orden en que pasaron, no en el del array")
    void seAplicanEnOrdenLocal() {
        EventoSyncRequest viejo = evento(LocalDateTime.now().minusDays(2), 10);
        EventoSyncRequest nuevo = evento(LocalDateTime.now().minusDays(1), 20);
        when(procesador.procesar(any(), any(), any()))
            .thenAnswer(inv -> ResultadoEventoSync.ok(inv.getArgument(0)));

        // El front los manda al revés: es exactamente lo que pasa cuando la cola se arma por
        // id de fila en vez de por fecha.
        SyncLoteResponse response = syncVentasService.sincronizar(ID_SYNC, lote(nuevo, viejo));

        assertEquals(List.of(viejo.getUuid(), nuevo.getUuid()),
            response.getResultados().stream().map(ResultadoEventoSync::getUuid).toList());
    }

    @Test
    @DisplayName("dos eventos del mismo instante los desempata la secuencia del dispositivo")
    void laSecuenciaDesempata() {
        LocalDateTime mismoInstante = LocalDateTime.now().minusHours(3);
        EventoSyncRequest primero = evento(mismoInstante, 41);
        EventoSyncRequest segundo = evento(mismoInstante, 42);
        when(procesador.procesar(any(), any(), any()))
            .thenAnswer(inv -> ResultadoEventoSync.ok(inv.getArgument(0)));

        SyncLoteResponse response = syncVentasService.sincronizar(ID_SYNC, lote(segundo, primero));

        assertEquals(List.of(primero.getUuid(), segundo.getUuid()),
            response.getResultados().stream().map(ResultadoEventoSync::getUuid).toList());
    }

    @Test
    @DisplayName("un lote por encima del tope se rechaza entero, con el límite en el mensaje")
    void loteDemasiadoGrande() {
        List<EventoSyncRequest> eventos = new ArrayList<>();
        for (int i = 0; i < 101; i++) {
            eventos.add(evento(LocalDateTime.now().minusHours(1), i));
        }

        BadRequestException e = assertThrows(BadRequestException.class,
            () -> syncVentasService.sincronizar(ID_SYNC,
                SyncLoteRequest.builder().dispositivo("caja-01").eventos(eventos).build()));

        // El número tiene que estar en el mensaje: es lo que le permite al front trocear sin
        // adivinarlo ni versionarlo por su cuenta.
        assertTrue(e.getMessage().contains("100"), e.getMessage());
        assertTrue(e.getMessage().contains("101"), e.getMessage());
    }

    @Test
    @DisplayName("un lote de exactamente el tope entra")
    void loteEnElLimiteEntra() {
        List<EventoSyncRequest> eventos = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            eventos.add(evento(LocalDateTime.now().minusHours(1), i));
        }
        when(procesador.procesar(any(), any(), any()))
            .thenAnswer(inv -> ResultadoEventoSync.ok(inv.getArgument(0)));

        SyncLoteResponse response = syncVentasService.sincronizar(ID_SYNC,
            SyncLoteRequest.builder().dispositivo("caja-01").eventos(eventos).build());

        assertEquals(100, response.getResultados().size());
    }

    @Test
    @DisplayName("un fallo inesperado se devuelve como INTERNO reintentable, sin tumbar el lote")
    void falloInesperadoEsInterno() {
        EventoSyncRequest evento = evento(LocalDateTime.now().minusHours(1), 1);
        when(procesador.procesar(any(), any(), any()))
            .thenThrow(new IllegalStateException("la base se cayó"));

        SyncLoteResponse response = syncVentasService.sincronizar(ID_SYNC, lote(evento));

        ResultadoEventoSync resultado = response.getResultados().get(0);
        assertEquals("ERROR", resultado.getEstado());
        assertEquals(CodigoErrorSync.INTERNO, resultado.getCodigo());
        assertTrue(resultado.isReintentable());
        // El detalle interno no viaja al front.
        assertTrue(!resultado.getMensaje().contains("la base se cayó"));
    }

    @Test
    @DisplayName("el dispositivo del lote y el usuario del JWT bajan a cada evento")
    void elDispositivoYElUsuarioBajanAlEvento() {
        EventoSyncRequest evento = evento(LocalDateTime.now().minusHours(1), 1);
        when(procesador.procesar(any(), any(), any())).thenReturn(ResultadoEventoSync.ok(evento));

        syncVentasService.sincronizar(ID_SYNC, lote(evento));

        verify(procesador).procesar(evento, "caja-01", ID_SYNC);
    }

    private EventoSyncRequest evento(LocalDateTime ocurridoEn, long secuencia) {
        return EventoSyncRequest.builder()
            .uuid(Uuid7.nuevo())
            .tipo(TipoEventoSync.CREAR)
            .ocurridoEn(ocurridoEn)
            .secuencia(secuencia)
            .build();
    }

    private SyncLoteRequest lote(EventoSyncRequest... eventos) {
        return SyncLoteRequest.builder()
            .dispositivo("caja-01")
            .eventos(new ArrayList<>(List.of(eventos)))
            .build();
    }
}
