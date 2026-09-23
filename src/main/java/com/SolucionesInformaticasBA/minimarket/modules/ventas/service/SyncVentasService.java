package com.SolucionesInformaticasBA.minimarket.modules.ventas.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.SolucionesInformaticasBA.minimarket.modules.ventas.api.SyncVentasApi;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.api.dto.EventoSyncRequest;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.api.dto.ResultadoEventoSync;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.api.dto.SyncLoteRequest;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.api.dto.SyncLoteResponse;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.enums.CodigoErrorSync;
import com.SolucionesInformaticasBA.minimarket.shared.exeption.BadRequestException;

import lombok.RequiredArgsConstructor;

/**
 * Recibe el lote de una caja que estuvo sin conexión y lo aplica evento por evento.
 *
 * <p>Este servicio <b>no</b> es transaccional a propósito. Cada evento es su propia transacción
 * —la abre {@link ProcesadorEventoSync}— y lo que pasa acá es la orquestación: ordenar, acotar
 * el tamaño del lote y armar un resultado por ítem. Un evento que falla no arrastra a los
 * demás, que es lo que permite responder siempre {@code 200} con el detalle de qué entró y qué
 * no.
 */
@Service
@RequiredArgsConstructor
public class SyncVentasService implements SyncVentasApi {

    private static final Logger log = LoggerFactory.getLogger(SyncVentasService.class);

    private final ProcesadorEventoSync procesador;

    /**
     * Tope de eventos por lote.
     *
     * <p>No es por el tamaño del payload —cien tickets de cinco líneas son unos 70 KB— sino por
     * cuánto tiempo el request retiene la base: cada evento toma locks de fila sobre stock y
     * lotes. Cien eventos son un par de segundos de trabajo secuencial; doscientos empiezan a
     * ser un request del que nadie sabe si se colgó.
     */
    @Value("${sync.lote.maximo:100}")
    private int loteMaximo;

    @Override
    public SyncLoteResponse sincronizar(UUID idUsuarioSync, SyncLoteRequest request) {
        List<EventoSyncRequest> eventos = request.getEventos();

        // El límite va en el mensaje para que el front trocee sin tener que adivinarlo ni
        // versionarlo por su cuenta. Un lote rechazado por tamaño no pierde nada: sigue en la
        // cola del dispositivo.
        if (eventos.size() > loteMaximo) {
            throw new BadRequestException(
                "El lote trae %d eventos y el máximo es %d: mandalo en partes"
                    .formatted(eventos.size(), loteMaximo));
        }

        List<ResultadoEventoSync> resultados = new ArrayList<>(eventos.size());
        for (EventoSyncRequest evento : ordenar(eventos)) {
            resultados.add(procesarSinPropagar(evento, request.getDispositivo(), idUsuarioSync));
        }

        return SyncLoteResponse.builder()
            .recibidos(eventos.size())
            .resultados(resultados)
            .build();
    }

    /**
     * Los eventos se aplican en el orden en que pasaron en el local, no en el orden del array.
     *
     * <p>Es lo que hace que un ticket y su anulación en el mismo lote se apliquen en el orden
     * correcto, y que la apertura de un turno llegue antes que los tickets que cuelgan de ella.
     * La secuencia del dispositivo desempata los que cayeron en el mismo instante, que es lo
     * habitual: un ticket y su anulación inmediata comparten el segundo.
     */
    private List<EventoSyncRequest> ordenar(List<EventoSyncRequest> eventos) {
        return eventos.stream()
            .sorted(Comparator.comparing(EventoSyncRequest::getOcurridoEn)
                .thenComparingLong(EventoSyncRequest::getSecuencia))
            .toList();
    }

    /**
     * Un evento que explota no puede tumbar el lote: el resto ya se aplicó o está por aplicarse,
     * y el front necesita saber qué pasó con cada uno para decidir qué borra de su cola.
     */
    private ResultadoEventoSync procesarSinPropagar(EventoSyncRequest evento, String dispositivo,
                                                    UUID idUsuarioSync) {
        try {
            return procesador.procesar(evento, dispositivo, idUsuarioSync);
        } catch (EventoSyncException e) {
            return ResultadoEventoSync.error(evento, e.getCodigo(), e.getMessage());
        } catch (RuntimeException e) {
            // Inesperado: se loguea entero de este lado y al front se le manda algo que pueda
            // mostrar, sin filtrar detalles internos. Reintentable, porque puede ser un
            // deadlock o una caída momentánea de la base.
            log.error("Fallo inesperado procesando el evento {} {}",
                evento.getTipo(), evento.getUuid(), e);
            return ResultadoEventoSync.error(evento, CodigoErrorSync.INTERNO,
                "No se pudo procesar el evento; volvé a intentarlo");
        }
    }
}
