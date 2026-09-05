package com.SolucionesInformaticasBA.minimarket.modules.proveedores.controller;

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

import com.SolucionesInformaticasBA.minimarket.modules.proveedores.api.ProveedoresApi;
import com.SolucionesInformaticasBA.minimarket.modules.proveedores.api.dto.ProveedorRequest;
import com.SolucionesInformaticasBA.minimarket.modules.proveedores.api.dto.ProveedorResponse;
import com.SolucionesInformaticasBA.minimarket.shared.Paginacion;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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

    /**
     * @param incluirBajas suma los proveedores dados de baja, que vienen con {@code deletedAt}
     *        cargado. Es cómo el front encuentra el que hay que restaurar: en el listado
     *        normal no aparecen.
     */
    @GetMapping("/v1")
    public ResponseEntity<Page<ProveedorResponse>> getAll(
            @RequestParam(defaultValue = "false") boolean incluirBajas,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = Paginacion.PAGE_MIN) int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = Paginacion.SIZE_MIN)
                @Max(value = Paginacion.MAX_PAGE_SIZE, message = Paginacion.SIZE_MAX) int size) {
        // Por nombre, con el id como desempate: dos proveedores pueden tener el mismo nombre
        // si uno está dado de baja, y sin desempate las filas se repiten al pasar de página.
        Pageable pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.ASC, "nombre").and(Sort.by(Sort.Direction.ASC, "id")));
        return ResponseEntity.ok(proveedoresApi.getAll(incluirBajas, pageable));
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

    /**
     * Baja lógica: deja de poder usarse en compras nuevas y de asignarse a productos, pero
     * sigue apareciendo en el historial de compras y puede volver con {@code /restaurar}.
     */
    @DeleteMapping("/v1/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        proveedoresApi.delete(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Vuelve a habilitar un proveedor dado de baja. Va sobre la fila original para no partir
     * el historial de compras, que lo referencia por id. Los dados de baja se listan con
     * {@code GET /api/proveedores/v1?incluirBajas=true}.
     */
    @PostMapping("/v1/{id}/restaurar")
    public ResponseEntity<ProveedorResponse> restaurar(@PathVariable UUID id) {
        return ResponseEntity.ok(proveedoresApi.restaurar(id));
    }
}
