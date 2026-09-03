package com.SolucionesInformaticasBA.minimarket.modules.caja.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.SolucionesInformaticasBA.minimarket.modules.caja.api.CajaApi;
import com.SolucionesInformaticasBA.minimarket.modules.caja.api.dto.CorteRequest;
import com.SolucionesInformaticasBA.minimarket.modules.caja.api.dto.CorteResponse;
import com.SolucionesInformaticasBA.minimarket.shared.SecurityUtils;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

@RestController
@RequestMapping("/api/caja")
@AllArgsConstructor
public class CorteController {
    private final CajaApi cajaApi;

    @PostMapping("/v1/corte")
    public ResponseEntity<CorteResponse> realizarCorte(
            @Valid @RequestBody CorteRequest request) {
        return ResponseEntity.ok(cajaApi.realizarCorte(SecurityUtils.getCurrentUserId(), request));
    }

    @GetMapping("/v1/corte/ultimo")
    public ResponseEntity<CorteResponse> getUltimo() {
        return ResponseEntity.ok(cajaApi.getUltimoCorte());
    }

    @GetMapping("/v1/corte/{id}")
    public ResponseEntity<CorteResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(cajaApi.getCorteById(id));
    }

    @GetMapping("/v1/corte/historial")
    public ResponseEntity<List<CorteResponse>> getHistorial() {
        return ResponseEntity.ok(cajaApi.getHistorialCortes());
    }
}
