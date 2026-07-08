package com.SolucionesInformaticasBA.minimarket.modules.usuario.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.SolucionesInformaticasBA.minimarket.modules.usuario.api.UsuarioApi;
import com.SolucionesInformaticasBA.minimarket.modules.usuario.api.dto.CrearUsuarioRequest;
import com.SolucionesInformaticasBA.minimarket.modules.usuario.api.dto.UsuarioResponse;
import com.SolucionesInformaticasBA.minimarket.modules.usuario.entity.Usuario;
import com.SolucionesInformaticasBA.minimarket.modules.usuario.enums.Rol;
import com.SolucionesInformaticasBA.minimarket.modules.usuario.repository.UsuarioRepository;

import lombok.AllArgsConstructor;

@Service
@AllArgsConstructor
public class UsuarioServiceImpl implements UsuarioApi {
    private final UsuarioRepository usuarioRepository;

    public UsuarioResponse crearUsuario(CrearUsuarioRequest request) {
        if (!checkUsername(request.getUsername())) {
            throw new IllegalArgumentException("El username ya existe");
        }

        Usuario usuario = toEntity(request);
        Usuario guardado = usuarioRepository.save(usuario);
        return toResponse(guardado);
    }

    public UsuarioResponse getUsuario(UUID id) {
        Usuario usuario = usuarioRepository.findByIdAndFechaEliminacionIsNull(id);
        if (usuario == null) {
            throw new IllegalArgumentException("Usuario no encontrado");
        }
        return toResponse(usuario);
    }

    public List<UsuarioResponse> listarUsuarios() {
        return usuarioRepository.findAllByFechaEliminacionIsNull()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public UsuarioResponse actualizarUsuario(UUID id, CrearUsuarioRequest request) {
        Usuario usuario = usuarioRepository.findByIdAndFechaEliminacionIsNull(id);
        if (usuario == null) {
            throw new IllegalArgumentException("Usuario no encontrado");
        }

        Usuario existenteConUsername = usuarioRepository.findByUsernameAndFechaEliminacionIsNull(request.getUsername());
        if (existenteConUsername != null && !existenteConUsername.getId().equals(id)) {
            throw new IllegalArgumentException("El username ya existe");
        }

        usuario.setUsername(request.getUsername());
        usuario.setPasswordHash(request.getPassword());
        Usuario actualizado = usuarioRepository.save(usuario);
        return toResponse(actualizado);
    }

    public void eliminarUsuario(UUID id) {
        Usuario usuario = usuarioRepository.findByIdAndFechaEliminacionIsNull(id);
        if (usuario == null) {
            throw new IllegalArgumentException("Usuario no encontrado");
        }

        usuario.setFechaEliminacion(LocalDateTime.now());
        usuarioRepository.save(usuario);
    }

    public boolean checkUsername(String username) {
        return usuarioRepository.findByUsernameAndFechaEliminacionIsNull(username) == null;
    }

    private Usuario toEntity(CrearUsuarioRequest dto) {
        return Usuario.builder()
                .username(dto.getUsername())
                .passwordHash(dto.getPassword())
                .rol(Rol.EMPLEADO)
                .build();
    }

    private UsuarioResponse toResponse(Usuario usuario) {
        return UsuarioResponse.builder()
                .username(usuario.getUsername())
                .rol(usuario.getRol().name())
                .build();
    }
}
