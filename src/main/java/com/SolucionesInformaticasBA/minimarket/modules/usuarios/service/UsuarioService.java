package com.SolucionesInformaticasBA.minimarket.modules.usuarios.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.SolucionesInformaticasBA.minimarket.modules.usuarios.api.UsuarioApi;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.api.dto.*;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.entity.Usuario;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.repository.UsuarioRepository;
import com.SolucionesInformaticasBA.minimarket.shared.exeption.BadRequestException;
import com.SolucionesInformaticasBA.minimarket.shared.exeption.ResourceNotFoundException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UsuarioService implements UsuarioApi {

    private final UsuarioRepository userRepository;
    private final PasswordEncoder passwordEncoder;

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
        return userRepository.findAll().stream()
                .filter(user -> user.getDeletedAt() == null)
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

    @Override
    @Transactional
    public void delete(UUID id) {
        Usuario u = findActiveUser(id);
        u.setDeletedAt(LocalDateTime.now());
        userRepository.save(u);
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
                .enabled(u.isEnabled())
                .createdAt(u.getCreatedAt())
                .updatedAt(u.getUpdatedAt())
                .build();
    }
}
