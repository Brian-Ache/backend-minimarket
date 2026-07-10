package com.SolucionesInformaticasBA.minimarket.modules.lotes.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.SolucionesInformaticasBA.minimarket.modules.lotes.api.LoteApi;
import com.SolucionesInformaticasBA.minimarket.modules.lotes.api.dto.LoteRequest;
import com.SolucionesInformaticasBA.minimarket.modules.lotes.api.dto.LoteResponse;

import io.swagger.v3.oas.annotations.parameters.RequestBody;
import lombok.AllArgsConstructor;

@RestController
@RequestMapping("/api/lotes")
@AllArgsConstructor
public class LoteController {
    private final LoteApi loteApi;

    @PostMapping("/v1")
    public ResponseEntity<LoteResponse> crear(
        @RequestHeader("idUsuario") UUID idUsuario,
        @RequestBody LoteRequest request
    ) {
        return ResponseEntity.ok(loteApi.crear(idUsuario, request));
    }

    @GetMapping("/v1")
    public ResponseEntity<List<LoteResponse>> getAll(){
        return ResponseEntity.ok(loteApi.getAll());
    }

    @GetMapping("/v1/vencimiento/proximos")
    public ResponseEntity<List<LoteResponse>> proximos(){
        return ResponseEntity.ok(loteApi.getByEstado("proximo"));
    }

    @GetMapping("/v1/vencimiento/vencidos")
    public ResponseEntity<List<LoteResponse>> vencidos(){
        return ResponseEntity.ok(loteApi.getByEstado("vencido"));
    }

    @GetMapping("/v1/vencimiento/vigentes")
    public ResponseEntity<List<LoteResponse>> vigentes(){
        return ResponseEntity.ok(loteApi.getByEstado("vigente"));
    }

    @GetMapping("/v1/estado/{estado}")
    public ResponseEntity<List<LoteResponse>> getByEstado(@PathVariable String estado){
        return ResponseEntity.ok(loteApi.getByEstado(estado));
    }
}
