package com.SolucionesInformaticasBA.minimarket.shared;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * Generador de UUID versión 7 (RFC 9562), el único formato de identificador que el sistema
 * acepta en {@code ventas} y {@code detalles_ventas}.
 *
 * <p><b>Por qué no el UUID de siempre.</b> Un v4 es aleatorio de punta a punta, y como la PK
 * es el clustered index de InnoDB, cada inserción cae en una página al azar: el índice se
 * fragmenta y la tabla que más crece del sistema es justo la que peor lo pasa. Un v7 lleva
 * los 48 bits altos con el timestamp en milisegundos, así que los ids nacen casi ordenados
 * y las inserciones vuelven a ser secuenciales.
 *
 * <p><b>Por qué escrito a mano y no {@code @UuidGenerator}.</b> Hibernate 7 sí trae
 * {@code Style.VERSION_7}, pero no sirve acá: un generador <b>genera siempre</b>, y pisaría el
 * uuid que trae un ticket creado sin conexión. El id de una venta es asignado —lo pone el
 * backend cuando la venta nace online y lo pone el front cuando nace offline—, así que el
 * generador de la entidad no es el lugar donde puede vivir esta decisión. Lo que hace falta es
 * poder pedir un v7 en una línea de código, y eso es esta clase.
 *
 * <p><b>El timestamp de acá no es dato de negocio.</b> La fecha de una venta es su columna
 * {@code created_at} y nada más; los bits del uuid no se leen nunca para saber cuándo pasó
 * algo. Son dos cosas distintas y mezclarlas es el error que esta nota quiere evitar.
 */
public final class Uuid7 {

    /** El nibble de versión, en su lugar: bits 48 a 51 contando desde la izquierda. */
    private static final long VERSION_7 = 0x7000L;

    /** La variante RFC 9562 ("10" en los dos bits más altos de la mitad baja). */
    private static final long VARIANTE_RFC = 0x8000000000000000L;

    /** Los 62 bits de la mitad baja que quedan para el azar, una vez puesta la variante. */
    private static final long MASCARA_ALEATORIA = 0x3FFFFFFFFFFFFFFFL;

    /** El contador vive en los 12 bits que sobran de la mitad alta. */
    private static final int CONTADOR_MAXIMO = 0xFFF;

    private static final SecureRandom ALEATORIO = new SecureRandom();

    private static long ultimoMilisegundo = -1L;
    private static int contador = 0;

    private Uuid7() {
    }

    /**
     * Un uuid v7 nuevo, estrictamente mayor que el anterior que haya devuelto esta clase.
     *
     * <p>La monotonía no sale sola del timestamp: dos llamadas seguidas caen casi siempre en
     * el mismo milisegundo, y ahí el orden lo decidirían los bits aleatorios. Por eso los 12
     * bits que siguen a la versión llevan un contador que avanza dentro del milisegundo, que
     * es el método que la propia RFC recomienda.
     *
     * <p>Sincronizado porque ese contador es estado compartido. El costo es un lock sin
     * contención real: se toma una vez por venta, no una vez por fila leída.
     */
    public static synchronized UUID nuevo() {
        long ahora = System.currentTimeMillis();

        if (ahora > ultimoMilisegundo) {
            ultimoMilisegundo = ahora;
            // Arranque aleatorio, pero en la mitad baja del rango: deja lugar para 2048 ids
            // más en el mismo milisegundo sin desbordar, y evita que el id siguiente sea
            // adivinable a partir del anterior.
            contador = ALEATORIO.nextInt(CONTADOR_MAXIMO >>> 1);
        } else {
            // Mismo milisegundo, o un reloj que fue para atrás —NTP ajustando, por ejemplo—.
            // En los dos casos seguimos hacia adelante: retroceder rompería la monotonía y
            // podría repetir un id ya entregado.
            contador++;
            if (contador > CONTADOR_MAXIMO) {
                ultimoMilisegundo++;
                contador = 0;
            }
        }

        long altos = (ultimoMilisegundo << 16) | VERSION_7 | contador;
        long bajos = (ALEATORIO.nextLong() & MASCARA_ALEATORIA) | VARIANTE_RFC;
        return new UUID(altos, bajos);
    }

    /**
     * Si el uuid es versión 7. Es la validación que se le hace a todo identificador que llega
     * del front: un v4 colado fragmenta el índice igual que si no hubiéramos hecho nada.
     */
    public static boolean esV7(UUID uuid) {
        return uuid != null && uuid.version() == 7;
    }
}
