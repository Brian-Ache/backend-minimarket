package com.SolucionesInformaticasBA.minimarket.shared.exeption;

public class UnauthorizedException extends RuntimeException{
    public UnauthorizedException(String mensaje){
        super(mensaje);
    }
}
