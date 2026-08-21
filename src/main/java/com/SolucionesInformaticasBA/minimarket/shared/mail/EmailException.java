package com.SolucionesInformaticasBA.minimarket.shared.mail;

/**
 * El envío de un mail falló con el SMTP configurado. Da 502: el pedido era válido, lo que falló
 * es un servicio de afuera, y quien lo hizo tiene que poder distinguir eso de un dato mal
 * cargado para saber que corresponde reintentar.
 */
public class EmailException extends RuntimeException {

    public EmailException(String message, Throwable cause) {
        super(message, cause);
    }
}
