package com.SolucionesInformaticasBA.minimarket.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.SolucionesInformaticasBA.minimarket.dto.request.LoteRequestDTO;
import com.SolucionesInformaticasBA.minimarket.dto.response.LoteResponseDTO;
import com.SolucionesInformaticasBA.minimarket.model.entity.Lote;
import com.SolucionesInformaticasBA.minimarket.service.LoteService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/lotes")
@RequiredArgsConstructor
public class LoteController {

    private final LoteService loteService;

    @PostMapping
    public ResponseEntity<LoteResponseDTO> crear(
            @RequestBody LoteRequestDTO request,
            @RequestHeader("usuarioId") Long usuarioId
    ) {

        return ResponseEntity.ok(loteService.crearLote(request, usuarioId));
    }

    @GetMapping
    public ResponseEntity<List<LoteResponseDTO>> getAll() {
        return ResponseEntity.ok(loteService.getAll());
    }

    @GetMapping("/vencimiento/por-vencer")
    public ResponseEntity<List<LoteResponseDTO>> proximos(
            @RequestParam(defaultValue = "7") int dias
    ) {

        return ResponseEntity.ok(loteService.proximosAVencer(dias));
    }

    @GetMapping("/vencimientos/vencidos")
    public ResponseEntity<List<LoteResponseDTO>>vencidos(
            @RequestParam(defaultValue = "0") int dias
    ) {

        return ResponseEntity.ok(loteService.vencidos(dias));
    }

    @GetMapping("/vencimientos/vigentes")
    public ResponseEntity<List<LoteResponseDTO>> vigentes() {
        return ResponseEntity.ok(loteService.vigentes());
    }

}