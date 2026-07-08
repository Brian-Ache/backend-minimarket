package com.SolucionesInformaticasBA.minimarket.shared;

import java.util.UUID;

import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtils {
    
    private SecurityUtils() {};

    public static UUID getCurrentUserId(){
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if(authentication != null && authentication.getPrincipal() instanceof String userId) {
            return UUID.fromString(userId);
        }
        throw new IllegalStateException("Usuario no autenticado");
    }
}
