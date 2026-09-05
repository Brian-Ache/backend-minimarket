package com.SolucionesInformaticasBA.minimarket.modules.ventas.controller;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.SolucionesInformaticasBA.minimarket.modules.ventas.api.VentasApi;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.api.dto.CobrarVentaRequest;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.api.dto.CobrarVentaResponse;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.api.dto.ResumenDiarioResponse;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.api.dto.VentaRequest;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.api.dto.VentaResponse;
import com.SolucionesInformaticasBA.minimarket.shared.Paginacion;
import com.SolucionesInformaticasBA.minimarket.shared.SecurityUtils;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;

@RestController
@RequestMapping("/api/ventas")
@AllArgsConstructor
public class VentaController {

    private final VentasApi ventasApi;

    @PostMapping("/v1")
    public ResponseEntity<VentaResponse> realizarVenta(
            @Valid @RequestBody VentaRequest request) {
        return ResponseEntity.ok(ventasApi.realizarVenta(SecurityUtils.getCurrentUserId(), request));
    }

    @GetMapping("/v1/{id}")
    public ResponseEntity<VentaResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ventasApi.getById(id));
    }

    /**
     * Un empleado ve solo sus ventas; un administrador, las de todos. El alcance no se acepta
     * del cliente: sale del rol de quien pregunta.
     */
    @GetMapping("/v1")
    public ResponseEntity<Page<VentaResponse>> getAll(
            @RequestParam(defaultValue = "0") @Min(value = 0, message = Paginacion.PAGE_MIN) int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = Paginacion.SIZE_MIN)
                @Max(value = Paginacion.MAX_PAGE_SIZE, message = Paginacion.SIZE_MAX) int size) {
        return ResponseEntity.ok(ventasApi.getAll(alcance(), pagina(page, size)));
    }

    @GetMapping("/v1/usuario/{idUsuario}")
    @PreAuthorize("hasRole('ADMIN') or #idUsuario.toString() == authentication.principal")
    public ResponseEntity<Page<VentaResponse>> getByUsuario(
            @PathVariable UUID idUsuario,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = Paginacion.PAGE_MIN) int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = Paginacion.SIZE_MIN)
                @Max(value = Paginacion.MAX_PAGE_SIZE, message = Paginacion.SIZE_MAX) int size) {
        return ResponseEntity.ok(ventasApi.getAll(idUsuario, pagina(page, size)));
    }

    @GetMapping("/v1/fecha")
    public ResponseEntity<Page<VentaResponse>> getByFecha(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime hasta,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = Paginacion.PAGE_MIN) int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = Paginacion.SIZE_MIN)
                @Max(value = Paginacion.MAX_PAGE_SIZE, message = Paginacion.SIZE_MAX) int size) {
        return ResponseEntity.ok(ventasApi.getByFecha(alcance(), desde, hasta, pagina(page, size)));
    }

    @PostMapping("/v1/{id}/cobrar")
    public ResponseEntity<CobrarVentaResponse> cobrar(
            @PathVariable UUID id,
            @Valid @RequestBody CobrarVentaRequest request) {
        return ResponseEntity.ok(ventasApi.cobrar(id, SecurityUtils.getCurrentUserId(), request));
    }

    @GetMapping("/v1/resumen/diario")
    public ResponseEntity<ResumenDiarioResponse> getResumenDiario(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) Optional<LocalDate> fecha) {
        return ResponseEntity.ok(ventasApi.getResumenDiario(fecha.orElse(LocalDate.now())));
    }

    /**
     * Desglose por medio de pago de un turno de caja. Complementa el corte, que solo cuenta
     * efectivo: acá se ve cuánto se cobró con tarjeta y transferencia en ese mismo turno.
     */
    @GetMapping("/v1/resumen/sesion/{idSesion}")
    public ResponseEntity<ResumenDiarioResponse> getResumenPorSesion(@PathVariable UUID idSesion) {
        return ResponseEntity.ok(ventasApi.getResumenPorSesion(idSesion));
    }

    @DeleteMapping("/v1/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        ventasApi.delete(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Null para un administrador —ve todas— y el id propio para cualquier otro rol. Es lo que
     * mantiene el mismo criterio en los dos listados sin que el cliente pueda elegir el alcance.
     */
    private UUID alcance() {
        return SecurityUtils.esAdmin() ? null : SecurityUtils.getCurrentUserId();
    }

    /**
     * De la más reciente a la más vieja, con el id como desempate: las ventas de un mismo
     * momento comparten createdAt, y sin segundo criterio el orden dentro de un empate lo elige
     * la base, con lo que las filas se repiten o se saltean al pasar de página.
     */
    private Pageable pagina(int page, int size) {
        return PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.ASC, "id")));
    }
}
