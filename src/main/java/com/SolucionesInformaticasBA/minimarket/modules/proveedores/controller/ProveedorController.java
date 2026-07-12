package com.SolucionesInformaticasBA.minimarket.modules.proveedores.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.SolucionesInformaticasBA.minimarket.modules.proveedores.api.ProveedoresApi;
import com.SolucionesInformaticasBA.minimarket.modules.proveedores.api.dto.ProveedorRequest;
import com.SolucionesInformaticasBA.minimarket.modules.proveedores.api.dto.ProveedorResponse;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

@RestController
@RequestMapping("/api/proveedores")
@AllArgsConstructor
public class ProveedorController {
    private final ProveedoresApi proveedoresApi;

    @PostMapping("/v1")
    public ResponseEntity<ProveedorResponse> crear(@Valid @RequestBody ProveedorRequest request) {
        return ResponseEntity.ok(proveedoresApi.crear(request));
    }

    @GetMapping("/v1")
    public ResponseEntity<List<ProveedorResponse>> getAll() {
        return ResponseEntity.ok(proveedoresApi.getAll());
    }

    @GetMapping("/v1/{id}")
    public ResponseEntity<ProveedorResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(proveedoresApi.getById(id));
    }

    @PutMapping("/v1/{id}")
    public ResponseEntity<ProveedorResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody ProveedorRequest request) {
        return ResponseEntity.ok(proveedoresApi.update(id, request));
    }

    @DeleteMapping("/v1/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        proveedoresApi.delete(id);
        return ResponseEntity.noContent().build();
    }
}
