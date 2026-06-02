package com.SolucionesInformaticasBA.minimarket.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.SolucionesInformaticasBA.minimarket.dto.request.AjusteStockRequestDTO;
import com.SolucionesInformaticasBA.minimarket.service.AjusteStockService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/stock")
@RequiredArgsConstructor
public class AjusteStockController {

    private final AjusteStockService ajusteStockService;

    @PostMapping("/ajuste")
    public ResponseEntity<String> ajustarStock(
            @RequestBody AjusteStockRequestDTO request,
            @RequestHeader("usuarioId") Long usuarioId
    ) {

        ajusteStockService.ajustarStock(request, usuarioId);

        return ResponseEntity.ok("Stock ajustado correctamente");
    }
}