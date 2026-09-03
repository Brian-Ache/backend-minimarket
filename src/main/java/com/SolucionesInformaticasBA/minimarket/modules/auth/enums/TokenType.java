package com.SolucionesInformaticasBA.minimarket.modules.auth.enums;

public enum TokenType {

    /** Reseteo de contraseña olvidada. Vive una hora: el usuario lo pidió y lo está esperando. */
    PASSWORD_RESET,

    /**
     * Invitación de un administrador. Con este token el invitado <b>define su contraseña</b> y
     * su nombre de usuario: hasta ese momento la cuenta no tiene una contraseña que sirva.
     */
    INVITATION
}
