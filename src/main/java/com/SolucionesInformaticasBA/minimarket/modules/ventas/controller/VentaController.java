package com.SolucionesInformaticasBA.minimarket.modules.ventas.controller;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
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

import com.SolucionesInformaticasBA.minimarket.modules.ventas.api.VentasApi;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.api.dto.CobrarVentaRequest;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.api.dto.CobrarVentaResponse;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.api.dto.ResumenDiarioResponse;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.api.dto.VentaRequest;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.api.dto.VentaResponse;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

@RestController
@RequestMapping("/api/ventas")
@AllArgsConstructor
public class VentaController {

    private final VentasApi ventasApi;

    @PostMapping("/v1")
    public ResponseEntity<VentaResponse> realizarVenta(
            @RequestHeader("idUsuario") UUID idUsuario,
            @Valid @RequestBody VentaRequest request) {
        return ResponseEntity.ok(ventasApi.realizarVenta(idUsuario, request));
    }

    @GetMapping("/v1/{id}")
    public ResponseEntity<VentaResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ventasApi.getById(id));
    }

    @GetMapping("/v1")
    public ResponseEntity<List<VentaResponse>> getAll() {
        return ResponseEntity.ok(ventasApi.getAll());
    }

    @GetMapping("/v1/usuario/{idUsuario}")
    public ResponseEntity<List<VentaResponse>> getByUsuario(@PathVariable UUID idUsuario) {
        return ResponseEntity.ok(ventasApi.getByUsuario(idUsuario));
    }

    @GetMapping("/v1/fecha")
    public ResponseEntity<List<VentaResponse>> getByFecha(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime hasta) {
        return ResponseEntity.ok(ventasApi.getByFecha(desde, hasta));
    }

    @PostMapping("/v1/{id}/cobrar")
    public ResponseEntity<CobrarVentaResponse> cobrar(
            @PathVariable UUID id,
            @RequestHeader UUID idUsuario,
            @Valid @RequestBody CobrarVentaRequest request) {
        return ResponseEntity.ok(ventasApi.cobrar(id, idUsuario, request));
    }

    @GetMapping("/v1/resumen/diario")
    public ResponseEntity<ResumenDiarioResponse> getResumenDiario(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) Optional<LocalDate> fecha) {
        return ResponseEntity.ok(ventasApi.getResumenDiario(fecha.orElse(LocalDate.now())));
    }

    @DeleteMapping("/v1/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        ventasApi.delete(id);
        return ResponseEntity.noContent().build();
    }
}
