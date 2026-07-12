package com.SolucionesInformaticasBA.minimarket.modules.categorias.controller;

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

import com.SolucionesInformaticasBA.minimarket.modules.categorias.api.CategoriasApi;
import com.SolucionesInformaticasBA.minimarket.modules.categorias.api.dto.CategoriaRequest;
import com.SolucionesInformaticasBA.minimarket.modules.categorias.api.dto.CategoriaResponse;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

@RestController
@RequestMapping("/api/categorias")
@AllArgsConstructor
public class CategoriaController {
    private final CategoriasApi categoriasApi;

    @PostMapping("/v1")
    public ResponseEntity<CategoriaResponse> crear(@Valid @RequestBody CategoriaRequest request) {
        return ResponseEntity.ok(categoriasApi.crear(request));
    }

    @GetMapping("/v1")
    public ResponseEntity<List<CategoriaResponse>> getAll() {
        return ResponseEntity.ok(categoriasApi.getAll());
    }

    @GetMapping("/v1/{id}")
    public ResponseEntity<CategoriaResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(categoriasApi.getById(id));
    }

    @PutMapping("/v1/{id}")
    public ResponseEntity<CategoriaResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody CategoriaRequest request) {
        return ResponseEntity.ok(categoriasApi.update(id, request));
    }

    @DeleteMapping("/v1/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        categoriasApi.delete(id);
        return ResponseEntity.noContent().build();
    }
}
