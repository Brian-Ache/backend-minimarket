package com.SolucionesInformaticasBA.minimarket.modules.compras.controller;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.SolucionesInformaticasBA.minimarket.modules.compras.api.CompraApi;
import com.SolucionesInformaticasBA.minimarket.modules.compras.api.dto.CompraRequest;
import com.SolucionesInformaticasBA.minimarket.modules.compras.api.dto.CompraResponse;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

@RestController
@RequestMapping("/api/compras")
@AllArgsConstructor
public class CompraController {
    private final CompraApi compraApi;

    @PostMapping("/v1")
    public ResponseEntity<CompraResponse> crear(
            @RequestHeader("idUsuario") UUID idUsuario,
            @Valid @RequestBody CompraRequest request) {
        return ResponseEntity.ok(compraApi.crear(idUsuario, request));
    }

    @GetMapping("/v1/{id}")
    public ResponseEntity<CompraResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(compraApi.getById(id));
    }

    @GetMapping("/v1")
    public ResponseEntity<List<CompraResponse>> getAll() {
        return ResponseEntity.ok(compraApi.getAll());
    }

    @GetMapping("/v1/usuario/{idUsuario}")
    public ResponseEntity<List<CompraResponse>> getByUsuario(@PathVariable UUID idUsuario) {
        return ResponseEntity.ok(compraApi.getByUsuario(idUsuario));
    }

    @GetMapping("/v1/fecha")
    public ResponseEntity<List<CompraResponse>> getByFecha(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime hasta) {
        return ResponseEntity.ok(compraApi.getByFecha(desde, hasta));
    }

    @DeleteMapping("/v1/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        compraApi.delete(id);
        return ResponseEntity.noContent().build();
    }
}
