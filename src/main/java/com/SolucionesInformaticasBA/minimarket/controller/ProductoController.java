package com.SolucionesInformaticasBA.minimarket.controller;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.SolucionesInformaticasBA.minimarket.dto.request.ProductoRequestDTO;
import com.SolucionesInformaticasBA.minimarket.dto.response.ProductoResponseDTO;
import com.SolucionesInformaticasBA.minimarket.service.ProductoService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/productos")
@RequiredArgsConstructor
public class ProductoController {

    private final ProductoService productoService;

    // 🟢 Crear producto
    @PostMapping
    public ResponseEntity<ProductoResponseDTO> crear(
            @RequestBody ProductoRequestDTO request,
            @RequestHeader("usuarioId") Long usuarioId
    ) {

        return ResponseEntity.ok(productoService.crear(request, usuarioId));
    }

    // 🔍 Obtener por ID
    @GetMapping("/{id}")
    public ResponseEntity<ProductoResponseDTO> getById(@PathVariable Long id) {

        return ResponseEntity.ok(productoService.getById(id));
    }

    // 📋 Listar todos
    @GetMapping
    public ResponseEntity<List<ProductoResponseDTO>> getAll() {

        return ResponseEntity.ok(productoService.getAll());
    }

    // 🔎 Buscar por barcode
    @GetMapping("/barcode/{barcode}")
    public ResponseEntity<ProductoResponseDTO> getByBarcode(@PathVariable String barcode) {

        return ResponseEntity.ok(productoService.getByBarcode(barcode));
    }

    // ✏️ Actualizar producto
    @PutMapping("/{id}")
    public ResponseEntity<ProductoResponseDTO> update(
            @PathVariable Long id,
            @RequestBody ProductoRequestDTO request
    ) {

        return ResponseEntity.ok(productoService.update(id, request));
    }

    // ❌ Eliminar (soft delete)
    @DeleteMapping("/{id}")
    public ResponseEntity<String> delete(@PathVariable Long id) {

        productoService.delete(id);

        return ResponseEntity.ok("Producto eliminado correctamente");
    }
}