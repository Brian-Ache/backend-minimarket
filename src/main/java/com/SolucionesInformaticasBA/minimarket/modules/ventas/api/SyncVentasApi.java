package com.SolucionesInformaticasBA.minimarket.modules.ventas.api;

import java.util.UUID;

import com.SolucionesInformaticasBA.minimarket.modules.ventas.api.dto.SyncLoteRequest;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.api.dto.SyncLoteResponse;

/**
 * Entrada de los tickets que se crearon sin conexión.
 *
 * <p>Va aparte de {@link VentasApi} y no adentro porque son dos contratos distintos: aquel es
 * la venta de mostrador, con el cajero delante de la pantalla, y este es la cola de una caja
 * que estuvo desconectada. Tener dos interfaces evita además que un mismo servicio tenga que
 * implementar las dos mitades.
 */
public interface SyncVentasApi {

    /**
     * Aplica un lote de eventos y devuelve un resultado por cada uno.
     *
     * @param idUsuarioSync quién está sincronizando, tomado del JWT. No es el vendedor de los
     *        tickets: con una terminal y cambio de turno, el que está logueado cuando vuelve
     *        internet no es el que hizo lo que está en la cola.
     */
    SyncLoteResponse sincronizar(UUID idUsuarioSync, SyncLoteRequest request);
}
