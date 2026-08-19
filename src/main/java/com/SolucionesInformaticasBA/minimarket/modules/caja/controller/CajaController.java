package com.SolucionesInformaticasBA.minimarket.modules.caja.controller;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.SolucionesInformaticasBA.minimarket.modules.caja.api.CajaApi;
import com.SolucionesInformaticasBA.minimarket.modules.caja.api.dto.AbrirSesionRequest;
import com.SolucionesInformaticasBA.minimarket.modules.caja.api.dto.MovimientoCajaRequest;
import com.SolucionesInformaticasBA.minimarket.modules.caja.api.dto.MovimientoCajaResponse;
import com.SolucionesInformaticasBA.minimarket.modules.caja.api.dto.ResumenCajaResponse;
import com.SolucionesInformaticasBA.minimarket.modules.caja.api.dto.SesionCajaResponse;
import com.SolucionesInformaticasBA.minimarket.shared.SecurityUtils;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

@RestController
@RequestMapping("/api/caja")
@AllArgsConstructor
public class CajaController {
    private final CajaApi cajaApi;

    @PostMapping("/v1/abrir")
    public ResponseEntity<SesionCajaResponse> abrirSesion(
            @Valid @RequestBody AbrirSesionRequest request) {
        return ResponseEntity.ok(cajaApi.abrirSesion(SecurityUtils.getCurrentUserId(), request));
    }

    @GetMapping("/v1/sesion-activa")
    public ResponseEntity<SesionCajaResponse> getSesionActiva() {
        return ResponseEntity.ok(cajaApi.getSesionActiva());
    }

    @PostMapping("/v1/entradas")
    public ResponseEntity<MovimientoCajaResponse> entradaManual(
            @Valid @RequestBody MovimientoCajaRequest request) {
        return ResponseEntity.ok(cajaApi.registrarEntradaManual(SecurityUtils.getCurrentUserId(), request));
    }

    @PostMapping("/v1/salidas")
    public ResponseEntity<MovimientoCajaResponse> salidaManual(
            @Valid @RequestBody MovimientoCajaRequest request) {
        return ResponseEntity.ok(cajaApi.registrarSalidaManual(SecurityUtils.getCurrentUserId(), request));
    }

    @GetMapping("/v1/movimientos")
    public ResponseEntity<List<MovimientoCajaResponse>> getMovimientos(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Optional<LocalDateTime> desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Optional<LocalDateTime> hasta) {
        return ResponseEntity.ok(cajaApi.getMovimientos(desde.orElse(null), hasta.orElse(null)));
    }

    /** Estado del turno abierto. Es lo que se mira antes de cerrar la caja. */
    @GetMapping("/v1/resumen/sesion")
    public ResponseEntity<ResumenCajaResponse> getResumenSesion() {
        return ResponseEntity.ok(cajaApi.getResumenSesion());
    }

    /** Resumen de un día, aunque no haya ninguna caja abierta. */
    @GetMapping("/v1/resumen/diario")
    public ResponseEntity<ResumenCajaResponse> getResumenDiario(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) Optional<LocalDate> fecha) {
        return ResponseEntity.ok(cajaApi.getResumenDiario(fecha.orElse(LocalDate.now())));
    }
}
