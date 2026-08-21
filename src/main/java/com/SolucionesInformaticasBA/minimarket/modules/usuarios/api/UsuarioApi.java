package com.SolucionesInformaticasBA.minimarket.modules.usuarios.api;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.SolucionesInformaticasBA.minimarket.modules.usuarios.api.dto.ActualizarUsuarioRequest;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.api.dto.CambiarPasswordRequest;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.api.dto.CambiarRolRequest;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.api.dto.CrearUsuarioRequest;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.api.dto.UsuarioResponse;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.entity.Usuario;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.enums.Rol;

public interface UsuarioApi {
    UsuarioResponse crear(CrearUsuarioRequest request);

    Usuario getUsuarioById(UUID id);

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
