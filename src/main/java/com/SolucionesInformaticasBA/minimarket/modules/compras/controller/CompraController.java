package com.SolucionesInformaticasBA.minimarket.modules.compras.controller;

import java.time.LocalDateTime;
import java.util.List;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.SolucionesInformaticasBA.minimarket.modules.compras.api.CompraApi;
import com.SolucionesInformaticasBA.minimarket.modules.compras.api.dto.CompraRequest;
import com.SolucionesInformaticasBA.minimarket.modules.compras.api.dto.CompraResponse;
import com.SolucionesInformaticasBA.minimarket.modules.compras.api.dto.ProveedorDeProductoResponse;
import com.SolucionesInformaticasBA.minimarket.shared.Paginacion;
import com.SolucionesInformaticasBA.minimarket.shared.SecurityUtils;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
            @RequestParam(defaultValue = "0") @Min(value = 0, message = Paginacion.PAGE_MIN) int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = Paginacion.SIZE_MIN)
                @Max(value = Paginacion.MAX_PAGE_SIZE, message = Paginacion.SIZE_MAX) int size) {

        Sort sort = "asc".equals(sortTotal)
            ? Sort.by(Sort.Direction.ASC, "total")
            : "desc".equals(sortTotal)
                ? Sort.by(Sort.Direction.DESC, "total")
                : Sort.by(Sort.Direction.DESC, "createdAt");

        // Con el id como desempate: por total la colisión es casi segura, y las compras
        // cargadas en el mismo lote comparten createdAt. Sin desempate, el orden dentro de un
        // empate lo elige la base y las filas se repiten o se saltean al pasar de página.
        Pageable pageable = PageRequest.of(page, size, sort.and(Sort.by(Sort.Direction.ASC, "id")));

        return ResponseEntity.ok(compraApi.getAllFiltered(proveedor, tipoComprobante, desde, hasta, pageable));
    }

    /**
     * A quién se le puede comprar este producto y a cuánto: el precio que cada proveedor lista
     * —el de referencia, que se carga a mano desde el catálogo— junto con lo que realmente se
     * le pagó la última vez.
     *
     * <p>Es de consulta y no interviene en el alta de la compra: qué proveedor conviene lo
     * decide el usuario. Sin paginar a propósito, porque la cantidad de proveedores de un
     * producto es del orden de la decena.
     */
    @GetMapping("/v1/producto/{idProducto}/proveedores")
    public ResponseEntity<List<ProveedorDeProductoResponse>> getProveedoresDeProducto(
            @PathVariable UUID idProducto) {
        return ResponseEntity.ok(compraApi.getProveedoresDeProducto(idProducto));
    }

    @DeleteMapping("/v1/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        compraApi.delete(id);
        return ResponseEntity.noContent().build();
    }
}
