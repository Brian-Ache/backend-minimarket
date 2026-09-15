package com.SolucionesInformaticasBA.minimarket.shared;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Convenciones de los importes en {@code BigDecimal} y los pasajes desde y hacia el
 * {@code float} que todavía usa el resto del sistema.
 *
 * <p><b>Por qué existe.</b> Las ventas guardan su plata en {@code DECIMAL(12,2)} y la manejan
 * como {@code BigDecimal}, porque el total lo suma también el front y las dos cuentas tienen
 * que dar exactamente igual. Compras, caja y productos siguen en {@code float} —es la deuda
 * técnica #1 del roadmap—, así que hay fronteras donde hay que cruzar de un mundo al otro, y
 * conviene que crucen todas por el mismo lugar.
 *
 * <p><b>La trampa que esta clase evita.</b> {@code BigDecimal.valueOf(precio)} sobre un
 * {@code float} lo ensancha primero a {@code double}, y ahí aparece la basura que el
 * {@code float} venía escondiendo: 1200.05f termina en 1200.0499877929688. Sumando cien líneas
 * así, el total del backend deja de coincidir con el del front y cada venta se marcaría para
 * revisión sin tener nada. El pasaje correcto es por {@link Float#toString}, que da el decimal
 * más corto que vuelve al mismo {@code float}: exactamente lo que se quiso guardar.
 */
public final class Importes {

    /** Dos decimales, como la columna. */
    public static final int ESCALA = 2;

    /** El redondeo de siempre para plata: 0.005 sube. */
    public static final RoundingMode REDONDEO = RoundingMode.HALF_UP;

    /** Cero con la escala de la columna, para arrancar acumuladores. */
    public static final BigDecimal CERO = BigDecimal.ZERO.setScale(ESCALA);

    private Importes() {
    }

    /** Un {@code float} de otro módulo —el precio de un producto, su costo— como importe. */
    public static BigDecimal de(float valor) {
        return new BigDecimal(Float.toString(valor)).setScale(ESCALA, REDONDEO);
    }

    /** Igual, tolerando el null que significa "no hay dato" (un costo sin cargar). */
    public static BigDecimal deNullable(Float valor) {
        return valor == null ? null : de(valor.floatValue());
    }

    /**
     * De vuelta a {@code float}, para las fronteras que todavía lo piden: el movimiento de
     * caja de una venta en efectivo y los reportes. Acá se pierde precisión, y es a propósito
     * que se vea en el código dónde pasa.
     */
    public static float aFloat(BigDecimal valor) {
        return valor == null ? 0f : valor.floatValue();
    }

    /** Deja un importe en la escala de la columna. */
    public static BigDecimal normalizar(BigDecimal valor) {
        return valor == null ? null : valor.setScale(ESCALA, REDONDEO);
    }

    /** El importe de una línea: precio por cantidad, ya normalizado. */
    public static BigDecimal porCantidad(BigDecimal precioUnitario, int cantidad) {
        return normalizar(precioUnitario.multiply(BigDecimal.valueOf(cantidad)));
    }
}
