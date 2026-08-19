package com.SolucionesInformaticasBA.minimarket.modules.usuarios.api;

import java.util.List;
import java.util.UUID;

import com.SolucionesInformaticasBA.minimarket.modules.usuarios.api.dto.ActualizarUsuarioRequest;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.api.dto.CambiarPasswordRequest;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.api.dto.CrearUsuarioRequest;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.api.dto.UsuarioResponse;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.entity.Usuario;

public interface UsuarioApi {
    UsuarioResponse crear(CrearUsuarioRequest request);

    Usuario getUsuarioById(UUID id);

    UsuarioResponse getById(UUID id);

    UsuarioResponse getByEmail(String email);

    List<UsuarioResponse> getAll();

    UsuarioResponse update(UUID id, ActualizarUsuarioRequest request);

    void delete(UUID id);

    void changePassword(UUID id, CambiarPasswordRequest request);

    boolean existById(UUID id);

    /** Activo (sin baja lógica) y habilitado. Lo consulta el filtro JWT en cada request. */
    boolean puedeOperar(UUID id);
}
