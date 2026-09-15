package com.SolucionesInformaticasBA.minimarket.modules.ventas.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.SolucionesInformaticasBA.minimarket.modules.ventas.entity.AnulacionPendiente;

/**
 * Anulaciones que están esperando a su venta.
 *
 * <p>La tabla se mantiene chica sola: cada fila vive hasta que llega el {@code CREAR} del
 * ticket, y ahí se borra. Lo único que puede quedarse es una anulación huérfana —un
 * {@code ANULAR} cuyo {@code CREAR} nunca se va a mandar—, que no rompe nada y queda a la vista
 * para quien mire la tabla.
 */
public interface AnulacionPendienteRepository extends JpaRepository<AnulacionPendiente, UUID> {
}
