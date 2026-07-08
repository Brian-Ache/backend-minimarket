package com.SolucionesInformaticasBA.minimarket.modules.auth.service;

import org.springframework.stereotype.Service;

import com.SolucionesInformaticasBA.minimarket.modules.usuario.api.UsuarioApi;
import com.SolucionesInformaticasBA.minimarket.modules.usuario.api.dto.CrearUsuarioRequest;
import com.SolucionesInformaticasBA.minimarket.modules.usuario.api.dto.UsuarioResponse;
import com.SolucionesInformaticasBA.minimarket.modules.usuario.entity.Usuario;
import com.SolucionesInformaticasBA.minimarket.modules.usuario.repository.UsuarioRepository;

import lombok.AllArgsConstructor;

@Service
@AllArgsConstructor
public class AuthService {

    private final UsuarioApi usuarioApi;
    private final UsuarioRepository usuarioRepository;
    private final JwtService jwtService;

    public UsuarioResponse registrarUsuario(CrearUsuarioRequest request) {
        return usuarioApi.crearUsuario(request);
    }

    public String login(String username, String password) {
        Usuario usuario = usuarioRepository.findByUsernameAndFechaEliminacionIsNull(username);
        if (usuario == null || !usuario.getPasswordHash().equals(password)) {
            throw new IllegalArgumentException("Credenciales inválidas");
        }

        return jwtService.generateToken(usuario.getUsername(), usuario.getRol().name());
    }

    public String recuperarContrasena(String username) {
        Usuario usuario = usuarioRepository.findByUsernameAndFechaEliminacionIsNull(username);
        if (usuario == null) {
            throw new IllegalArgumentException("Usuario no encontrado");
        }

        return jwtService.generatePasswordResetToken(usuario.getUsername());
    }
}
