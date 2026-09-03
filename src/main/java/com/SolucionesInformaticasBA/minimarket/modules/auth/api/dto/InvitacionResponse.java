package com.SolucionesInformaticasBA.minimarket.modules.auth.api.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Lo que el formulario de aceptación necesita saber antes de que la persona escriba nada: a
 * quién está saludando y qué nombre de usuario ofrecerle precargado.
 *
 * <p>Deliberadamente no lleva el rol ni el estado de la cuenta: lo devuelve un endpoint público,
 * cuya única credencial es el token del mail.
 */
@Data
@Builder
public class InvitacionResponse {

    private String nombre;

    private String apellido;

    private String email;

    /** El que se derivó del email al invitar. El invitado puede confirmarlo o cambiarlo. */
    private String usernameSugerido;
}
