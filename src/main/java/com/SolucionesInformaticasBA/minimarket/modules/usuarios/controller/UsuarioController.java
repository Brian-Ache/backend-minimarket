package com.SolucionesInformaticasBA.minimarket.modules.usuarios.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.SolucionesInformaticasBA.minimarket.modules.usuarios.api.UsuarioApi;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.api.dto.ActualizarUsuarioRequest;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.api.dto.CambiarPasswordRequest;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.api.dto.CrearUsuarioRequest;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.api.dto.UsuarioResponse;
import com.SolucionesInformaticasBA.minimarket.shared.SecurityUtils;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UsuarioController {

    private final UsuarioApi usuarioApi;

    @PostMapping("/v1")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UsuarioResponse> crear(@Valid @RequestBody CrearUsuarioRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(usuarioApi.crear(request));
    }

    @GetMapping("/v1/me")
    public ResponseEntity<UsuarioResponse> getMe() {
        var userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(usuarioApi.getById(userId));
    }

    @GetMapping("/v1/{id}")
    @PreAuthorize("hasRole('ADMIN') or #id.toString() == authentication.principal")
    public ResponseEntity<UsuarioResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(usuarioApi.getById(id));
    }

    @GetMapping("/v1")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<UsuarioResponse>> getAll() {
        return ResponseEntity.ok(usuarioApi.getAll());
    }

    @PatchMapping("/v1/{id}")
    @PreAuthorize("hasRole('ADMIN') or #id.toString() == authentication.principal")
    public ResponseEntity<UsuarioResponse> update(@PathVariable UUID id,
            @Valid @RequestBody ActualizarUsuarioRequest request) {
        return ResponseEntity.ok(usuarioApi.update(id, request));
    }

    @DeleteMapping("/v1/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        usuarioApi.delete(id);
        return ResponseEntity.noContent().build();
    }

    // Solo el dueño: cambiar la contraseña exige conocer la actual, así que ni el ADMIN
    // puede hacerlo por otro (para eso está el flujo de reseteo).
    @PostMapping("/v1/{id}/change-password")
    @PreAuthorize("#id.toString() == authentication.principal")
    public ResponseEntity<Void> changePassword(@PathVariable UUID id,
            @Valid @RequestBody CambiarPasswordRequest request) {
        usuarioApi.changePassword(id, request);
        return ResponseEntity.ok().build();
    }
}
