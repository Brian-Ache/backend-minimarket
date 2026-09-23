package com.SolucionesInformaticasBA.minimarket.modules.auth.service;

import com.SolucionesInformaticasBA.minimarket.shared.exeption.BadRequestException;

/**
 * Se presentó un refresh token que ya había sido rotado, pasada la ventana de gracia, y por eso
 * se cerraron todas las sesiones del usuario.
 *
 * <p>Existe por una sola razón, y es transaccional: el rechazo tiene que salir como error pero
 * <b>sin voltear la revocación que acaba de hacerse</b>. Una {@link BadRequestException} común
 * revierte la transacción, y las sesiones que se cerraron por sospecha de robo volverían a
 * abrirse solas. {@code AuthService.refreshToken} la declara en {@code noRollbackFor} justamente
 * para eso.
 *
 * <p>Hereda de {@link BadRequestException} para que el {@code GlobalExceptionHandler} ya la
 * mapee a 400 y el cuerpo salga idéntico al de un token inválido cualquiera: quien tenga el token
 * robado no debería poder deducir que disparó la detección.
 *
 * <p>Es de paquete a propósito: es una señal entre {@code TokenService} y {@code AuthService}, no
 * un tipo de error de la API.
 */
class RefreshTokenReusadoException extends BadRequestException {

    RefreshTokenReusadoException(String message) {
        super(message);
    }
}
