package com.SolucionesInformaticasBA.minimarket.modules.ventas.api.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * La respuesta del lote, siempre {@code 200}.
 *
 * <p>Un evento que falla no arrastra a los demás: cada uno trae su propio resultado y el front
 * decide qué borrar de la cola y qué reintentar. Los únicos casos que no responden {@code 200}
 * son los del lote entero —JSON ilegible, lote vacío o por encima del tope—, donde no hay
 * resultados por ítem que devolver.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyncLoteResponse {

    /** Cuántos eventos se recibieron, para que el front pueda cotejar contra lo que mandó. */
    private int recibidos;

    /** Uno por evento, en el orden en que se procesaron (que no es el del array). */
    private List<ResultadoEventoSync> resultados;
}
