package com.SolucionesInformaticasBA.minimarket.shared.exeption;

public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException (String mensaje) {
        super(mensaje);
    }
}
