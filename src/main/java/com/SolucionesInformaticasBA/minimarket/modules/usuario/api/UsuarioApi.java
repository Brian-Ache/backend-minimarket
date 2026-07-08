package com.SolucionesInformaticasBA.minimarket.modules.usuario.api;

import java.util.List;
import java.util.UUID;

import com.SolucionesInformaticasBA.minimarket.modules.usuario.api.dto.CrearUsuarioRequest;
import com.SolucionesInformaticasBA.minimarket.modules.usuario.api.dto.UsuarioResponse;

public interface UsuarioApi {

    UsuarioResponse crearUsuario(CrearUsuarioRequest request);

    List<UsuarioResponse> listarUsuarios();

    UsuarioResponse getUsuario(UUID id);

    UsuarioResponse actualizarUsuario(UUID id, CrearUsuarioRequest request);

    void eliminarUsuario(UUID id);
}
