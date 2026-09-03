package com.SolucionesInformaticasBA.minimarket.modules.usuarios.enums;

/**
 * Situación de una cuenta. Reemplaza al viejo flag {@code enabled}, que solo distinguía
 * "verificado" de "no verificado" y no dejaba lugar para el bloqueo: dar de baja a alguien
 * temporalmente obligaba a borrarlo.
 *
 * <p>Es independiente del borrado lógico ({@code deleted_at}): un usuario bloqueado sigue
 * existiendo y conserva su historial de ventas, compras y movimientos.
 */
public enum EstadoUsuario {

    /** Creado pero todavía sin acceso: falta que confirme su cuenta y defina su contraseña. */
    PENDIENTE,

    /** Opera normalmente. */
    ACTIVO,

    /** Acceso suspendido por un administrador. Puede reactivarse. */
    BLOQUEADO;

    public boolean puedeOperar() {
        return this == ACTIVO;
    }
}
