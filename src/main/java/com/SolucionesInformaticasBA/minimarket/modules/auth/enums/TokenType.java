package com.SolucionesInformaticasBA.minimarket.modules.auth.enums;

public enum TokenType {

    /** Autorregistro: confirma que el email existe. La contraseña ya la eligió el usuario. */
    VERIFICATION,

    PASSWORD_RESET,

    /**
     * Invitación de un administrador. A diferencia de VERIFICATION, con este token el invitado
     * además <b>define su contraseña</b>: hasta ese momento la cuenta no tiene una que sirva.
     */
    INVITATION
}
