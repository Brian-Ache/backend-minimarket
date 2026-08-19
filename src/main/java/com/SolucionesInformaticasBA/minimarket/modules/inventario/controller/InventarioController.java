package com.SolucionesInformaticasBA.minimarket.modules.inventario.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.SolucionesInformaticasBA.minimarket.modules.inventario.api.InventarioApi;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.api.dto.AjusteStockRequest;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.api.dto.LoteRequest;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.api.dto.LoteResponse;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.api.dto.MovimientoStockRequest;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.api.dto.MovimientoStockResponse;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.api.dto.StockRequest;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.api.dto.StockResponse;
import com.SolucionesInformaticasBA.minimarket.shared.SecurityUtils;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

@RestController
@RequestMapping("/api/inventario")
@AllArgsConstructor
public class InventarioController {
    private final InventarioApi inventarioApi;

    @PostMapping("/v1/stock")
    public ResponseEntity<StockResponse> crearStock(@Valid @RequestBody StockRequest request){
        return ResponseEntity.ok(inventarioApi.crear(request));
    }

    @GetMapping("/v1/stock/{idProducto}")
    public ResponseEntity<StockResponse> getStock(@PathVariable UUID idProducto){
        return ResponseEntity.ok(inventarioApi.getByIdProducto(idProducto));
    }

    // El idUsuario que venga en el body se ignora: la identidad sale siempre del JWT.
    @PutMapping("/v1/stock/aumentar")
    public ResponseEntity<StockResponse> aumentarStock(@Valid @RequestBody MovimientoStockRequest request){
        request.setIdUsuario(SecurityUtils.getCurrentUserId());
        return ResponseEntity.ok(inventarioApi.aumentar(request));
    }

    @PutMapping("/v1/stock/disminuir")
    public ResponseEntity<StockResponse> disminuirStock(@Valid @RequestBody MovimientoStockRequest request){
        request.setIdUsuario(SecurityUtils.getCurrentUserId());
        return ResponseEntity.ok(inventarioApi.disminuir(request));
    }

    @DeleteMapping("/v1/stock/{idProducto}")
    public ResponseEntity<Void> deleteStock(@PathVariable UUID idProducto){
        inventarioApi.delete(idProducto);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/v1/controlar")
    public ResponseEntity<String> controlarStock(
            @Valid @RequestBody AjusteStockRequest request){
        inventarioApi.controlarStock(SecurityUtils.getCurrentUserId(), request);
        return ResponseEntity.ok("Stock controlado correctamente");
    }

    @GetMapping("/v1/movimientos/{idProducto}")
    public ResponseEntity<List<MovimientoStockResponse>> obtenerMovimientos(@PathVariable UUID idProducto){
        return ResponseEntity.ok(inventarioApi.obtenerMovimientos(idProducto));
    }

    @PostMapping("/v1/lotes")
    public ResponseEntity<LoteResponse> crearLote(@Valid @RequestBody LoteRequest request){
        return ResponseEntity.ok(inventarioApi.crear(request));
    }

    @GetMapping("/v1/lotes")
    public ResponseEntity<List<LoteResponse>> getAllLotes(){
        return ResponseEntity.ok(inventarioApi.getAll());
    }

    @GetMapping("/v1/lotes/estado/{estado}")
    public ResponseEntity<List<LoteResponse>> getLotesByEstado(@PathVariable String estado){
        return ResponseEntity.ok(inventarioApi.getByEstado(estado));
    }

    @GetMapping("/v1/lotes/vencimiento/proximos")
    public ResponseEntity<List<LoteResponse>> lotesProximos(){
        return ResponseEntity.ok(inventarioApi.getByEstado("PROXIMO"));
    }

    @GetMapping("/v1/lotes/vencimiento/vencidos")
    public ResponseEntity<List<LoteResponse>> lotesVencidos(){
        return ResponseEntity.ok(inventarioApi.getByEstado("VENCIDO"));
    }

    @GetMapping("/v1/lotes/vencimiento/vigentes")
    public ResponseEntity<List<LoteResponse>> lotesVigentes(){
        return ResponseEntity.ok(inventarioApi.getByEstado("VIGENTE"));
    }
}
