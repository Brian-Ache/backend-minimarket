package com.SolucionesInformaticasBA.minimarket.shared;

import java.util.UUID;

import org.springframework.security.core.context.SecurityContextHolder;

import com.SolucionesInformaticasBA.minimarket.shared.exeption.UnauthorizedException;

public final class SecurityUtils {

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
}
