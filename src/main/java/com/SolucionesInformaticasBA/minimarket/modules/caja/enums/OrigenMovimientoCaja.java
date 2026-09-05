package com.SolucionesInformaticasBA.minimarket.modules.caja.enums;

/**
 * Qué originó el movimiento de caja.
 *
 * <p>Era un {@code String} que viajaba entre caja, ventas y compras, y el resumen clasificaba
 * comparando por igualdad exacta: un {@code "Venta"} mal tipeado compilaba, pasaba la validación
 * de Java y quedaba fuera de todas las categorías del arqueo aunque sí sumara al saldo esperado.
 *
 * <p>Los nombres son los mismos que acepta el CHECK de {@code movimientos_caja.origen}, así que
 * la columna sigue guardando exactamente lo que guardaba.
 */
public enum OrigenMovimientoCaja {
    /** Lo carga una persona: fondo para vuelto, un gasto suelto. */
    MANUAL,
    /** Entrada automática al cobrar una venta en efectivo. */
    VENTA,
    /** Salida automática al pagar una compra con la plata de la caja. */
    COMPRA,
    /** Contrapartida de una anulación: devuelve al turno lo que había movido el comprobante. */
    REVERSA,
    /** Lo que se saca de la caja al cerrar el turno. Lo que no se retira queda para el siguiente. */
    RETIRO
}
