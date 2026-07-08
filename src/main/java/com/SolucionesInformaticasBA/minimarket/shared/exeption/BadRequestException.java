package com.SolucionesInformaticasBA.minimarket.shared.exeption;

public class BadRequestException extends RuntimeException{
    public BadRequestException(String mensaje) {
        super(mensaje);
    }
}
