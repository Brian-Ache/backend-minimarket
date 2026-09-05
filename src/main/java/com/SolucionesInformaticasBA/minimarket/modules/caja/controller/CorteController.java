package com.SolucionesInformaticasBA.minimarket.modules.caja.controller;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.SolucionesInformaticasBA.minimarket.modules.caja.api.CajaApi;
import com.SolucionesInformaticasBA.minimarket.modules.caja.api.dto.CorteRequest;
import com.SolucionesInformaticasBA.minimarket.modules.caja.api.dto.CorteResponse;
import com.SolucionesInformaticasBA.minimarket.shared.Paginacion;
import com.SolucionesInformaticasBA.minimarket.shared.SecurityUtils;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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

    /**
     * Del corte más reciente al más viejo, con el id como desempate: dos turnos pueden cerrar
     * en el mismo instante —o quedar con {@code fechaCierre} nula en filas viejas— y sin
     * desempate las filas se repiten o se saltean al pasar de página.
     */
    @GetMapping("/v1/corte/historial")
    public ResponseEntity<Page<CorteResponse>> getHistorial(
            @RequestParam(defaultValue = "0") @Min(value = 0, message = Paginacion.PAGE_MIN) int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = Paginacion.SIZE_MIN)
                @Max(value = Paginacion.MAX_PAGE_SIZE, message = Paginacion.SIZE_MAX) int size) {
        Pageable pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "fechaCierre").and(Sort.by(Sort.Direction.ASC, "id")));
        return ResponseEntity.ok(cajaApi.getHistorialCortes(pageable));
    }
}
