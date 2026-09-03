package com.SolucionesInformaticasBA.minimarket.modules.auth.enums;

public enum TokenType {

    /**
     * Confirma que el email existe, sin tocar la contraseña: la cuenta ya tiene una.
     *
     * <p>Sin emisor hoy. Lo emitía el autorregistro, que se eliminó; queda el circuito de
     * validación ({@code AuthApi.verifyEmail}) esperando a quien vuelva a necesitarlo.
     */
    VERIFICATION,

    /** Reseteo de contraseña olvidada. Vive una hora: el usuario lo pidió y lo está esperando. */
    PASSWORD_RESET,

    /**
     * Invitación de un administrador. A diferencia de VERIFICATION, con este token el invitado
     * además <b>define su contraseña</b>: hasta ese momento la cuenta no tiene una que sirva.
     */
    INVITATION
}
