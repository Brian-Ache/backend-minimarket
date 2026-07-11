package com.SolucionesInformaticasBA.minimarket.modules.ventas.controller;

import com.SolucionesInformaticasBA.minimarket.dto.request.VentaRequestDTO;
import com.SolucionesInformaticasBA.minimarket.dto.response.VentaResponseDTO;
import com.SolucionesInformaticasBA.minimarket.service.VentaService;

import lombok.RequiredArgsConstructor;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ventas")
@RequiredArgsConstructor
public class VentaController {

    private final VentaService ventaService;

    
    @PostMapping//POST http://localhost:8080/api/ventas
    public ResponseEntity<VentaResponseDTO> realizarVenta(
            @RequestBody VentaRequestDTO request,
            @RequestHeader("usuarioId") Long usuarioId
    ) {

        VentaResponseDTO response = ventaService.realizarVenta(request, usuarioId);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<VentaResponseDTO> getVenta(@PathVariable Long id) {
        return ResponseEntity.ok(ventaService.getById(id));
    }
    
    @GetMapping
    public ResponseEntity<List<VentaResponseDTO>> getAll() {
        return ResponseEntity.ok(ventaService.getAll());
    }
}
