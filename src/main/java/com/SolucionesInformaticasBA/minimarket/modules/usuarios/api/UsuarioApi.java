package com.SolucionesInformaticasBA.minimarket.modules.usuarios.api;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.SolucionesInformaticasBA.minimarket.modules.usuarios.api.dto.ActualizarUsuarioRequest;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.api.dto.CambiarPasswordRequest;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.api.dto.CambiarRolRequest;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.api.dto.CrearUsuarioRequest;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.api.dto.InvitarUsuarioRequest;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.api.dto.UsuarioResponse;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.enums.Rol;

public interface UsuarioApi {

    /** Alta directa, con contraseña elegida por quien la crea. Ver también {@link #invitar}. */
    UsuarioResponse crear(CrearUsuarioRequest request);

    UsuarioResponse invitar(InvitarUsuarioRequest request);

    void reenviarInvitacion(UUID id);

    UsuarioResponse getById(UUID id);

    UsuarioResponse getByEmail(String email);

    List<UsuarioResponse> getAll();

    UsuarioResponse update(UUID id, ActualizarUsuarioRequest request);

    UsuarioResponse cambiarRol(UUID id, CambiarRolRequest request);

    UsuarioResponse bloquear(UUID id);

    UsuarioResponse desbloquear(UUID id);

    void delete(UUID id);

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

    /** Marca la cuenta como ACTIVO. La usa la verificación de email. */
    void activarCuenta(UUID id);

    /**
     * Define la contraseña de una cuenta que todavía no tiene una propia y la habilita, en un
     * solo paso. Es la contracara de {@link #invitar}.
     *
     * @throws com.SolucionesInformaticasBA.minimarket.shared.exeption.BadRequestException si la
     *         cuenta se dio de baja o se bloqueó entre la invitación y la aceptación.
     */
    void establecerPasswordInicial(UUID id, String password);

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
