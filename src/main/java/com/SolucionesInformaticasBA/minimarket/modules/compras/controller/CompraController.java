package com.SolucionesInformaticasBA.minimarket.modules.compras.controller;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
import com.SolucionesInformaticasBA.minimarket.shared.SecurityUtils;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

@RestController
@RequestMapping("/api/compras")
@AllArgsConstructor
public class CompraController {
    private final CompraApi compraApi;

    @PostMapping("/v1")
    public ResponseEntity<CompraResponse> crear(
            @Valid @RequestBody CompraRequest request) {
        return ResponseEntity.ok(compraApi.crear(SecurityUtils.getCurrentUserId(), request));
    }

    @GetMapping("/v1/{id}")
    public ResponseEntity<CompraResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(compraApi.getById(id));
    }

    /**
     * Búsqueda unificada de compras con filtros opcionales.
     * Reemplaza los endpoints GET /v1/fecha y GET /v1/usuario/{idUsuario}
     * con un solo endpoint que acepta todos los filtros como query params.
     *
     * Filtros:
     * - proveedor: UUID del proveedor (opcional)
     * - tipoComprobante: "REMITO", "FACTURA", etc. (opcional)
     * - desde/hasta: rango de fechas en formato ISO (opcional)
     * - sortTotal: "asc" o "desc" para ordenar por total (opcional, default: orden por fecha DESC)
     */
    @GetMapping("/v1")
    public ResponseEntity<Page<CompraResponse>> getAll(
            @RequestParam(required = false) UUID proveedor,
            @RequestParam(required = false) String tipoComprobante,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime hasta,
            @RequestParam(required = false) String sortTotal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Sort sort = "asc".equals(sortTotal)
            ? Sort.by(Sort.Direction.ASC, "total")
            : "desc".equals(sortTotal)
                ? Sort.by(Sort.Direction.DESC, "total")
                : Sort.by(Sort.Direction.DESC, "createdAt");
        Pageable pageable = PageRequest.of(page, size, sort);

        return ResponseEntity.ok(compraApi.getAllFiltered(proveedor, tipoComprobante, desde, hasta, pageable));
    }

    // ENDPOINTS COMENTADOS: Se reemplazaron por GET /v1 con filtros opcionales.
    // GET /v1/fecha → ahora se pasa desde/hasta como query params en GET /v1
    // GET /v1/usuario/{idUsuario} → ahora se pasa proveedor como query param en GET /v1
    // Se mantienen comentados por si en el futuro se necesitan rutas dedicadas.

    // @GetMapping("/v1/usuario/{idUsuario}")
    // public ResponseEntity<Page<CompraResponse>> getByUsuario(
    //         @PathVariable UUID idUsuario,
    //         @RequestParam(defaultValue = "0") int page,
    //         @RequestParam(defaultValue = "20") int size) {
    //     Pageable pageable = PageRequest.of(page, size);
    //     return ResponseEntity.ok(compraApi.getByUsuario(idUsuario, pageable));
    // }

    // @GetMapping("/v1/fecha")
    // public ResponseEntity<Page<CompraResponse>> getByFecha(
    //         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime desde,
    //         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime hasta,
    //         @RequestParam(defaultValue = "0") int page,
    //         @RequestParam(defaultValue = "20") int size) {
    //     Pageable pageable = PageRequest.of(page, size);
    //     return ResponseEntity.ok(compraApi.getByFecha(desde, hasta, pageable));
    // }

    @DeleteMapping("/v1/{id}")
    public ResponseEntity<Void> delete(
            @RequestHeader("idUsuario") UUID idUsuario,
            @PathVariable UUID id) {
        compraApi.delete(id, idUsuario);
        return ResponseEntity.noContent().build();
    }
}
