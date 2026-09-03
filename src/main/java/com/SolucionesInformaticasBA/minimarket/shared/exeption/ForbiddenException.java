package com.SolucionesInformaticasBA.minimarket.shared.exeption;

/**
 * El usuario está autenticado pero no tiene permiso para esta operación en particular. Da 403,
 * igual que un {@code AccessDeniedException} de Spring, con la diferencia de que conserva el
 * motivo: los permisos que dependen de la jerarquía de roles se deciden en el servicio, donde
 * se conoce el rol del objetivo, y ahí un "no tenés permisos" a secas no le dice nada a quien
 * lo recibe.
 */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
