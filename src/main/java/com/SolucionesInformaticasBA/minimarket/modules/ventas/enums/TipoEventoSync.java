package com.SolucionesInformaticasBA.minimarket.modules.ventas.enums;

/**
 * Los eventos que el front encola mientras no hay internet.
 *
 * <p>Los cuatro están en el contrato desde el principio, aunque los dos de caja se implementen
 * más adelante: el front necesita saber la forma completa del lote para escribir su cola una
 * sola vez, y un evento que el backend todavía no maneja responde {@code ERROR} reintentable,
 * que es justo lo que el front ya hace con cualquier otro error temporal.
 */
public enum TipoEventoSync {
    /** Un ticket, ya cobrado, tal como salió de la caja registradora. */
    CREAR,
    /** La anulación de un ticket, que puede llegar antes que el ticket mismo. */
    ANULAR,
    /** La apertura de un turno de caja que ocurrió sin conexión. */
    ABRIR_SESION,
    /** El corte de ese turno, con el conteo físico que hizo el cajero. */
    CERRAR_SESION
}
