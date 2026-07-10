package com.SolucionesInformaticasBA.minimarket.modules.stock.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.SolucionesInformaticasBA.minimarket.modules.stock.api.StockApi;
import com.SolucionesInformaticasBA.minimarket.modules.stock.api.dto.AjusteStockRequest;

import io.swagger.v3.oas.annotations.parameters.RequestBody;
import lombok.AllArgsConstructor;

@RestController
@RequestMapping("/api/stock")
@AllArgsConstructor
public class StockController {
    private final StockApi stockApi;

    @PostMapping("/v1/ajuste")
    public ResponseEntity<String> ajustarStock(
        @RequestHeader("idUsuario") UUID idUsuario,
        @RequestBody AjusteStockRequest request
    ) {
        stockApi.ajustarStock(idUsuario, request);
        return ResponseEntity.ok("Stock ajustado correctamente");
    }
}
