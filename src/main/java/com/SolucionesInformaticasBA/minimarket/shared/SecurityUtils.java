package com.SolucionesInformaticasBA.minimarket.shared;

import java.util.UUID;

import org.springframework.security.core.context.SecurityContextHolder;

import com.SolucionesInformaticasBA.minimarket.shared.exeption.UnauthorizedException;

public final class SecurityUtils {

    private static final String ROL_ADMIN = "ROLE_ADMIN";

    private SecurityUtils() {};

    /**
     * Id del usuario autenticado, tomado del JWT. Es la única fuente de identidad válida:
     * nunca confiar en un idUsuario enviado por el cliente.
     */
    public static UUID getCurrentUserId(){
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if(authentication != null && authentication.getPrincipal() instanceof String userId) {
            return UUID.fromString(userId);
        }
        throw new UnauthorizedException("Usuario no autenticado");
    }

    /**
     * Si quien está operando tiene mando de administrador. Un SUPERADMIN también lo tiene: la
     * jerarquía ya está materializada en las authorities que arma el filtro del JWT, así que
     * alcanza con preguntar por ROLE_ADMIN.
     *
     * <p>Sirve para los casos en que el rol no decide si se puede llamar al endpoint sino
     * cuánto se ve: un empleado consulta sus propias ventas, un administrador las de todos.
     */
    public static boolean esAdmin(){
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            throw new UnauthorizedException("Usuario no autenticado");
        }
        return authentication.getAuthorities().stream()
                .anyMatch(a -> ROL_ADMIN.equals(a.getAuthority()));
    }
}
