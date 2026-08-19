package com.SolucionesInformaticasBA.minimarket.modules.productos.controller;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

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

import com.SolucionesInformaticasBA.minimarket.modules.productos.api.ProductosApi;
import com.SolucionesInformaticasBA.minimarket.modules.productos.api.dto.ProductoRequest;
import com.SolucionesInformaticasBA.minimarket.modules.productos.api.dto.ProductoResponse;
import com.SolucionesInformaticasBA.minimarket.shared.SecurityUtils;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;


@RestController
@RequestMapping("/api/productos")
@AllArgsConstructor
public class ProductoController {
    private final ProductosApi productosApi;

    @PostMapping("/v1")
    public ResponseEntity<ProductoResponse> crear(
            @Valid @RequestBody ProductoRequest request) {
        return ResponseEntity.ok(productosApi.crear(SecurityUtils.getCurrentUserId(), request));
    }

    @GetMapping("/v1")
    public ResponseEntity<List<ProductoResponse>> getAll(
            @RequestParam Optional<UUID> categoria,
            @RequestParam Optional<UUID> proveedor) {
        if (categoria.isPresent() && proveedor.isPresent()) {
            return ResponseEntity.ok(productosApi.getByCategoriaAndProveedor(categoria.get(), proveedor.get()));
        }
        if (categoria.isPresent()) {
            return ResponseEntity.ok(productosApi.getByCategoria(categoria.get()));
        }
        if (proveedor.isPresent()) {
            return ResponseEntity.ok(productosApi.getByProveedor(proveedor.get()));
        }
        return ResponseEntity.ok(productosApi.getAll());
    }

    @GetMapping("/v1/{id}")
    public ResponseEntity<ProductoResponse> getById(@PathVariable UUID id){
        return ResponseEntity.ok(productosApi.getById(id));
    }

    @GetMapping("/v1/search")
    public ResponseEntity<List<ProductoResponse>> search(@RequestParam String q) {
        return ResponseEntity.ok(productosApi.search(q));
    }

    @GetMapping("/v1/barcode/{barcode}")
    public ResponseEntity<ProductoResponse> getByBarcode(@PathVariable String barcode){
        return ResponseEntity.ok(productosApi.getByBarcode(barcode));
    }

    @PutMapping("/v1/{id}")
    public ResponseEntity<ProductoResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody ProductoRequest request){
        return ResponseEntity.ok(productosApi.update(id, request));
    }

    @DeleteMapping("/v1/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id){
        productosApi.delete(id);
        return ResponseEntity.noContent().build();
    }
}
