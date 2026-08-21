package com.SolucionesInformaticasBA.minimarket.modules.usuarios.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.SolucionesInformaticasBA.minimarket.modules.auth.api.AuthApi;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.api.UsuarioApi;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.api.dto.*;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.entity.Usuario;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.enums.EstadoUsuario;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.enums.Rol;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.repository.UsuarioRepository;
import com.SolucionesInformaticasBA.minimarket.shared.SecurityUtils;
import com.SolucionesInformaticasBA.minimarket.shared.exeption.BadRequestException;
import com.SolucionesInformaticasBA.minimarket.shared.exeption.ResourceNotFoundException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UsuarioService implements UsuarioApi {

    private final UsuarioRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthApi authApi;

    /**
     * Alta de usuarios. Es exclusiva del ADMIN (ver UsuarioController), por eso el usuario
     * queda habilitado de entrada: no hay autorregistro ni verificación por email en el MVP.
     */
    @Override
    @Transactional
    public UsuarioResponse crear(CrearUsuarioRequest request) {
        if (userRepository.existsByEmailAndDeletedAtIsNull(request.getEmail())) {
            throw new BadRequestException("El email ya está registrado");
        }
        if (userRepository.existsByUsernameAndDeletedAtIsNull(request.getUsername())) {
            throw new BadRequestException("El nombre de usuario ya está en uso");
        }

        Usuario u = Usuario.builder()
                .nombre(request.getNombre())
                .apellido(request.getApellido())
                .email(request.getEmail())
                .username(request.getUsername())
                .hashPassword(passwordEncoder.encode(request.getPassword()))
                .rol(request.getRol() != null ? request.getRol() : Rol.EMPLEADO)
                // Lo crea un ADMIN con su contraseña, así que ya puede operar: no hay
                // circuito de verificación por email en el MVP.
                .estado(EstadoUsuario.ACTIVO)
                .build();

        // saveAndFlush: sin el flush, created_at/updated_at todavía no están en la entidad
        // y la respuesta del alta saldría con esos campos en null.
        return toUserResponse(userRepository.saveAndFlush(u));
    }

    @Override
    public Usuario getUsuarioById(UUID id){
        return findActiveUser(id);
    }

    @Override
    public UsuarioResponse getById(UUID id) {
        Usuario u = findActiveUser(id);
        return toUserResponse(u);
    }

    @Override
    public UsuarioResponse getByEmail(String email) {
        Usuario u = userRepository.findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
        return toUserResponse(u);
    }

    @Override
    public List<UsuarioResponse> getAll() {
        return userRepository.findAllByDeletedAtIsNull().stream()
                .map(this::toUserResponse)
                .toList();
    }

    @Override
    @Transactional
    public UsuarioResponse update(UUID id, ActualizarUsuarioRequest request) {
        Usuario u = findActiveUser(id);

        if (request.getNombre() != null) {
            u.setNombre(request.getNombre());
        }
        if (request.getApellido() != null) {
            u.setApellido(request.getApellido());
        }

        u = userRepository.save(u);
        return toUserResponse(u);
    }

    /**
     * Suspende el acceso sin borrar la cuenta: el usuario conserva su historial y puede
     * reactivarse. Cortar las sesiones es parte del bloqueo, si no seguiría operando con el
     * token que ya tenía en la mano.
     */
    @Override
    @Transactional
    public UsuarioResponse bloquear(UUID id) {
        Usuario u = findActiveUser(id);

        if (u.getEstado() == EstadoUsuario.BLOQUEADO) {
            throw new BadRequestException("El usuario ya está bloqueado");
        }
        if (id.equals(SecurityUtils.getCurrentUserId())) {
            throw new BadRequestException("No podés bloquear tu propia cuenta");
        }

        u.setEstado(EstadoUsuario.BLOQUEADO);
        u = userRepository.save(u);
        authApi.revokeAllSessions(id);

        return toUserResponse(u);
    }

    /** Devuelve el acceso a una cuenta bloqueada. Tiene que volver a iniciar sesión. */
    @Override
    @Transactional
    public UsuarioResponse desbloquear(UUID id) {
        Usuario u = findActiveUser(id);

        if (u.getEstado() != EstadoUsuario.BLOQUEADO) {
            throw new BadRequestException("El usuario no está bloqueado");
        }

        u.setEstado(EstadoUsuario.ACTIVO);
        return toUserResponse(userRepository.save(u));
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        Usuario u = findActiveUser(id);
        u.setDeletedAt(LocalDateTime.now());
        userRepository.save(u);

        // Sin esto el usuario dado de baja seguiría operando con sus tokens vigentes.
        authApi.revokeAllSessions(id);
    }

    @Override
    @Transactional
    public void changePassword(UUID id, CambiarPasswordRequest request) {
        Usuario u = findActiveUser(id);

        if (!passwordEncoder.matches(request.getPassActual(), u.getHashPassword())) {
            throw new BadRequestException("La contraseña actual no es correcta");
        }

        u.setHashPassword(passwordEncoder.encode(request.getNuevoPass()));
        userRepository.save(u);
    }

    public boolean existById(UUID id){
        return userRepository.existsByIdAndDeletedAtIsNull(id);
    }

    @Override
    public boolean puedeOperar(UUID id){
        return userRepository.existsByIdAndDeletedAtIsNullAndEstado(id, EstadoUsuario.ACTIVO);
    }

    private Usuario findActiveUser(UUID id) {
        Usuario u = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        if (u.getDeletedAt() != null) {
            throw new ResourceNotFoundException("Usuario no encontrado");
        }

        return u;
    }

    private UsuarioResponse toUserResponse(Usuario u) {
        return UsuarioResponse.builder()
                .id(u.getId())
                .nombre(u.getNombre())
                .apellido(u.getApellido())
                .username(u.getUsername())
                .email(u.getEmail())
                .rol(u.getRol())
                .estado(u.getEstado())
                .createdAt(u.getCreatedAt())
                .updatedAt(u.getUpdatedAt())
                .build();
    }
}
