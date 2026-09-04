package com.SolucionesInformaticasBA.minimarket.modules.inventario.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.SolucionesInformaticasBA.minimarket.modules.inventario.api.InventarioApi;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.api.dto.AjusteStockRequest;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.api.dto.LoteRequest;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.api.dto.LoteResponse;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.api.dto.MovimientoStockRequest;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.api.dto.MovimientoStockResponse;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.api.dto.StockRequest;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.api.dto.StockResponse;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.enums.TipoMovimiento;
import com.SolucionesInformaticasBA.minimarket.shared.SecurityUtils;
import com.SolucionesInformaticasBA.minimarket.shared.exeption.BadRequestException;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;

@RestController
@RequestMapping("/api/inventario")
@AllArgsConstructor
public class InventarioController {

    /** Techo del tamaño de página del historial de movimientos. */
    private static final int MAX_PAGE_SIZE = 100;

    private final InventarioApi inventarioApi;

    @PostMapping("/v1/stock")
    public ResponseEntity<StockResponse> crearStock(@Valid @RequestBody StockRequest request){
        return ResponseEntity.ok(inventarioApi.crear(request));
    }

    @GetMapping("/v1/stock/{idProducto}")
    public ResponseEntity<StockResponse> getStock(@PathVariable UUID idProducto){
        return ResponseEntity.ok(inventarioApi.getByIdProducto(idProducto));
    }

    @PostMapping("/v1/stock/batch")
    public ResponseEntity<List<StockResponse>> getStockBatch(@RequestBody List<UUID> idProductos){
        return ResponseEntity.ok(inventarioApi.getByIdProductos(idProductos));
    }

    @PutMapping("/v1/stock/aumentar")
    public ResponseEntity<StockResponse> aumentarStock(@Valid @RequestBody MovimientoStockRequest request){
        sanearMovimientoManual(request);
        return ResponseEntity.ok(inventarioApi.aumentar(request));
    }

    @PutMapping("/v1/stock/disminuir")
    public ResponseEntity<StockResponse> disminuirStock(@Valid @RequestBody MovimientoStockRequest request){
        sanearMovimientoManual(request);
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
    public ResponseEntity<Page<MovimientoStockResponse>> obtenerMovimientos(
            @PathVariable UUID idProducto,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "El número de página no puede ser negativo") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "El tamaño de página debe ser al menos 1")
                @Max(value = MAX_PAGE_SIZE, message = "El tamaño de página no puede superar " + MAX_PAGE_SIZE) int size){
        Pageable pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.ASC, "id")));
        return ResponseEntity.ok(inventarioApi.obtenerMovimientos(idProducto, pageable));
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

    /**
     * Un movimiento cargado a mano solo puede ser un ajuste o una merma. COMPRA y VENTA los
     * escribe el sistema con el id del comprobante que los originó, y son justo los que lee la
     * reversa de una anulación: aceptarlos del cliente permitía fabricar un movimiento que se
     * hacía pasar por el de una venta real. Por lo mismo la referencia nunca se toma del body.
     *
     * <p>El idUsuario también se pisa acá: la identidad sale siempre del JWT.
     */
    private void sanearMovimientoManual(MovimientoStockRequest request){
        TipoMovimiento tipo;
        try {
            tipo = TipoMovimiento.valueOf(request.getTipo());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new BadRequestException("Tipo de movimiento inválido: " + request.getTipo());
        }

        if (tipo != TipoMovimiento.AJUSTE && tipo != TipoMovimiento.MERMA) {
            throw new BadRequestException(
                "Un movimiento manual solo puede ser AJUSTE o MERMA: " + tipo + " lo registra el sistema");
        }

        request.setIdUsuario(SecurityUtils.getCurrentUserId());
        request.setIdReferencia(null);
    }
}
