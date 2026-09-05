package com.SolucionesInformaticasBA.minimarket.modules.usuarios.api;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.SolucionesInformaticasBA.minimarket.modules.usuarios.api.dto.ActualizarUsuarioRequest;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.api.dto.CambiarPasswordRequest;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.api.dto.CambiarRolRequest;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.api.dto.InvitarUsuarioRequest;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.api.dto.UsuarioResponse;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.enums.Rol;

public interface UsuarioApi {

    /**
     * Única alta del sistema: crea la cuenta en estado PENDIENTE y dispara el mail con el que
     * la persona define su propia contraseña. La contracara es
     * {@link #establecerPasswordInicial}.
     */
    UsuarioResponse invitar(InvitarUsuarioRequest request);

    void reenviarInvitacion(UUID id);

    UsuarioResponse getById(UUID id);

    UsuarioResponse getByEmail(String email);

    /**
     * @param incluirBajas suma las cuentas dadas de baja, que vienen con {@code deletedAt}
     *        cargado. Es lo que le permite al administrador encontrar una para
     *        {@link #restaurar}: son invisibles en el listado normal.
     */
    Page<UsuarioResponse> getAll(boolean incluirBajas, Pageable pageable);

    UsuarioResponse update(UUID id, ActualizarUsuarioRequest request);

    UsuarioResponse cambiarRol(UUID id, CambiarRolRequest request);

    UsuarioResponse bloquear(UUID id);

    UsuarioResponse desbloquear(UUID id);

    void delete(UUID id);

    /**
     * Revive una cuenta dada de baja y le manda una invitación nueva: vuelve como PENDIENTE,
     * con la contraseña anterior invalidada, y la persona define una nueva desde el mail.
     *
     * <p>Restaura la fila original en lugar de crear otra: el id del usuario es permanente
     * —ventas, compras, movimientos de caja y de stock lo referencian con {@code ON DELETE
     * RESTRICT}—, así que un alta nueva con el mismo email partiría su historial en dos. Por lo
     * mismo nunca hay colisión que resolver: el email y el username siguieron reservados por
     * las unique keys mientras la cuenta estaba de baja.
     *
     * <p>Rige la jerarquía de siempre: se restaura por debajo del propio nivel.
     */
    UsuarioResponse restaurar(UUID id);

    void changePassword(UUID id, CambiarPasswordRequest request);

    boolean existById(UUID id);

    boolean existsByEmail(String email);

    /**
     * Resuelve al usuario habilitado que corresponde a un identificador —email o nombre de
     * usuario, indistinto— y valida su contraseña. Vacío si no existe, si no puede operar
     * (dado de baja, pendiente o bloqueado) o si la contraseña no coincide.
     *
     * <p>Los tres casos se colapsan en un vacío a propósito: distinguirlos le diría a quien
     * prueba credenciales qué cuentas existen y en qué estado están.
     *
     * <p>La comparación vive acá y no en auth porque el formato del hash es de este módulo:
     * quien guarda la credencial es quien sabe cómo verificarla.
     */
    Optional<UsuarioResponse> verificarCredenciales(String identificador, String password);

    /**
     * Resuelve al usuario por email o por nombre de usuario, sin exigir que pueda operar. Lo
     * usa el reseteo de contraseña, que tiene que alcanzar también a una cuenta pendiente o
     * bloqueada. Para el login va {@link #verificarCredenciales}.
     */
    Optional<UsuarioResponse> buscarPorIdentificador(String identificador);

    /**
     * Datos de una cuenta invitada cuya invitación sigue en pie. Los usa el formulario de
     * aceptación para saludar a la persona y precargarle el nombre de usuario que se le
     * derivó del email.
     *
     * <p>Aplica la misma regla de vigencia que {@link #establecerPasswordInicial}, para que el
     * formulario no se muestre si al confirmar va a ser rechazado igual.
     *
     * @throws com.SolucionesInformaticasBA.minimarket.shared.exeption.BadRequestException si la
     *         cuenta se dio de baja o se bloqueó desde que se envió la invitación.
     */
    UsuarioResponse getCuentaInvitada(UUID id);

    /**
     * Define la contraseña de una cuenta que todavía no tiene una propia y la habilita, en un
     * solo paso. Es la contracara de {@link #invitar}.
     *
     * @param username nombre de usuario elegido por el invitado, o {@code null} para dejar el
     *        que se derivó del email al invitarlo.
     * @throws com.SolucionesInformaticasBA.minimarket.shared.exeption.BadRequestException si la
     *         cuenta se dio de baja o se bloqueó entre la invitación y la aceptación, o si el
     *         nombre de usuario elegido ya está tomado por otra cuenta.
     */
    void establecerPasswordInicial(UUID id, String password, String username);

    /**
     * Reemplaza la contraseña sin pedir la anterior. Es para el reseteo por email, donde la
     * prueba de identidad es el token; el cambio hecho por el propio usuario va por
     * {@link #changePassword}, que sí exige la actual.
     */
    void restablecerPassword(UUID id, String password);

    /**
     * Rol con el que el usuario opera hoy, o vacío si no puede operar (dado de baja, pendiente
     * o bloqueado). Lo consulta el filtro JWT en cada request para armar las authorities.
     *
     * <p>Devuelve el rol en lugar de un booleano a propósito: el rol también viaja como claim
     * del JWT, pero ese claim queda viejo apenas se cambia el rol del usuario, y un ADMIN
     * degradado seguiría mandando hasta que su token expire.
     */
    Optional<Rol> rolVigente(UUID id);
}
