package com.SolucionesInformaticasBA.minimarket.modules.categorias.controller;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.SolucionesInformaticasBA.minimarket.modules.categorias.api.CategoriasApi;
import com.SolucionesInformaticasBA.minimarket.modules.categorias.api.dto.CategoriaRequest;
import com.SolucionesInformaticasBA.minimarket.modules.categorias.api.dto.CategoriaResponse;
import com.SolucionesInformaticasBA.minimarket.shared.Paginacion;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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

    /** Por nombre, con el id como desempate para que la paginación sea estable. */
    @GetMapping("/v1")
    public ResponseEntity<Page<CategoriaResponse>> getAll(
            @RequestParam(defaultValue = "0") @Min(value = 0, message = Paginacion.PAGE_MIN) int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = Paginacion.SIZE_MIN)
                @Max(value = Paginacion.MAX_PAGE_SIZE, message = Paginacion.SIZE_MAX) int size) {
        Pageable pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.ASC, "nombre").and(Sort.by(Sort.Direction.ASC, "id")));
        return ResponseEntity.ok(categoriasApi.getAll(pageable));
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
