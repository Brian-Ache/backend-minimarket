package com.SolucionesInformaticasBA.minimarket.modules.ventas.enums;

/**
 * Por dónde entró la venta al sistema.
 *
 * <p>No es una distinción cosmética: una venta {@code OFFLINE} trae su fecha, su vendedor y su
 * sesión de caja en el payload —son hechos de hace dos días, no del momento de la llamada—, y
 * eso es una excepción a la regla de que la identidad siempre sale del JWT. Junto con
 * {@code id_usuario_sync} es lo que permite reconstruir después quién atribuyó qué.
 */
public enum OrigenVenta {
    /** Entró por POST /api/ventas/v1, con el cajero frente a la pantalla. */
    ONLINE,
    /** Se creó en el dispositivo sin conexión y llegó por el endpoint de sincronización. */
    OFFLINE
}
