package com.SolucionesInformaticasBA.minimarket.modules.ventas.enums;

import lombok.Getter;

/**
 * Por qué un evento del lote no se pudo aplicar, y si tiene sentido volver a mandarlo.
 *
 * <p>El {@code reintentable} es lo que le permite al front no reintentar para siempre un evento
 * condenado. Un {@code false} significa que mandarlo de nuevo va a fallar exactamente igual y
 * que hace falta que alguien intervenga; un {@code true}, que el evento es válido y lo que falta
 * es otra cosa —la sesión que todavía no llegó, el turno que sigue abierto, la base que se cayó
 * un segundo—.
 */
@Getter
public enum CodigoErrorSync {

    /** El uuid no es versión 7: un v4 fragmenta el índice igual que si no validáramos nada. */
    UUID_INVALIDO(false),

    /** Fecha futura, demasiado vieja, o una anulación anterior a su propio ticket. */
    FECHA_INVALIDA(false),

    /** El idVendedor no existe. Que esté dado de baja no es error: la venta ya ocurrió. */
    VENDEDOR_INEXISTENTE(false),

    /** Una línea referencia un producto que no existe. */
    PRODUCTO_INEXISTENTE(false),

    /** El ticket referencia una sesión de caja que todavía no llegó; puede venir en el lote siguiente. */
    SESION_INEXISTENTE(true),

    /** La apertura choca con el turno abierto que ya hay. Cuando ese cierre, esta entra. */
    SESION_YA_ABIERTA(true),

    /** El ticket llegó después del corte de su propio turno. Un corte firmado no se toca. */
    SESION_CERRADA(false),

    /** Un EMPLEADO intentó anular una venta ajena, o una fuera de su ventana de siete días. */
    PERMISO_INSUFICIENTE(false),


    /** El evento es de un tipo que esta versión del backend todavía no aplica. */
    NO_IMPLEMENTADO(true),

    /** El payload del evento no trae lo que su tipo necesita. */
    EVENTO_INVALIDO(false),

    /** Cualquier fallo inesperado del backend. */
    INTERNO(true);

    private final boolean reintentable;

    CodigoErrorSync(boolean reintentable) {
        this.reintentable = reintentable;
    }
}
