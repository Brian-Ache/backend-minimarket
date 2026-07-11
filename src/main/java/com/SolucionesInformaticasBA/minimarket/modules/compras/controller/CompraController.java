package com.SolucionesInformaticasBA.minimarket.modules.compras.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.SolucionesInformaticasBA.minimarket.dto.request.CompraRequestDTO;
import com.SolucionesInformaticasBA.minimarket.dto.response.CompraResponseDTO;
import com.SolucionesInformaticasBA.minimarket.service.CompraService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/compras")
@RequiredArgsConstructor
public class CompraController {

    private final CompraService compraService;

    // 🟢 Registrar compra
    @PostMapping
    public ResponseEntity<CompraResponseDTO> registrarCompra(
            @RequestBody CompraRequestDTO request,
            @RequestHeader("usuarioId") Long usuarioId
    ) {

        CompraResponseDTO response = compraService.registrarCompra(request, usuarioId);

        return ResponseEntity.ok(response);
    }

    // 🔍 Obtener compra por ID
    @GetMapping("/{id}")
    public ResponseEntity<CompraResponseDTO> getById(@PathVariable Long id) {

        return ResponseEntity.ok(compraService.getById(id));
    }

    // 📋 Listar todas las compras
    @GetMapping
    public ResponseEntity<List<CompraResponseDTO>> getAll() {

        return ResponseEntity.ok(compraService.getAll());
    }
}