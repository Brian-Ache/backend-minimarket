package com.SolucionesInformaticasBA.minimarket.modules.ventas.service;

import com.SolucionesInformaticasBA.minimarket.modules.ventas.enums.CodigoErrorSync;

import lombok.Getter;

/**
 * Un evento del lote que no se puede aplicar.
 *
 * <p>Es una excepción y no un valor de retorno por una razón concreta: al salir del método
 * transaccional que procesa el evento, hace que se deshaga todo lo que ese evento hubiera
 * escrito. Un ticket que falla a mitad de camino no puede dejar el stock descontado y la venta
 * sin guardar.
 *
 * <p>No la mira el {@code GlobalExceptionHandler}: la atrapa el servicio de sincronización y la
 * convierte en el resultado de ese ítem. El lote entero sigue respondiendo {@code 200}.
 */
@Getter
public class EventoSyncException extends RuntimeException {

    private final transient CodigoErrorSync codigo;

    public EventoSyncException(CodigoErrorSync codigo, String mensaje) {
        super(mensaje);
        this.codigo = codigo;
    }
}
