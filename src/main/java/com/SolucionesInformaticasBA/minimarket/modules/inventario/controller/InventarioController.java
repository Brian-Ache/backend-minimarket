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
import com.SolucionesInformaticasBA.minimarket.modules.inventario.api.dto.AjusteLotesRequest;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.api.dto.AjusteStockRequest;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.api.dto.LoteRequest;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.api.dto.LoteResponse;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.api.dto.MovimientoStockRequest;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.api.dto.MovimientoStockResponse;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.api.dto.StockResponse;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.enums.TipoMovimiento;
import com.SolucionesInformaticasBA.minimarket.shared.Paginacion;
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

    private final InventarioApi inventarioApi;

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
            @RequestParam(defaultValue = "0") @Min(value = 0, message = Paginacion.PAGE_MIN) int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = Paginacion.SIZE_MIN)
                @Max(value = Paginacion.MAX_PAGE_SIZE, message = Paginacion.SIZE_MAX) int size){
        Pageable pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.ASC, "id")));
        return ResponseEntity.ok(inventarioApi.obtenerMovimientos(idProducto, pageable));
    }

    @PostMapping("/v1/lotes")
    public ResponseEntity<LoteResponse> crearLote(@Valid @RequestBody LoteRequest request){
        return ResponseEntity.ok(inventarioApi.crear(request));
    }

    /**
     * Conteo físico de un producto que maneja lotes. Es el equivalente de {@code /controlar}
     * para estos productos, que no pueden ajustarse por ahí: su existencia es la suma de los
     * lotes, así que corregir el total sin decir de qué lote sale no significa nada.
     *
     * <p>Solo se ajustan los lotes que vienen en el request; el resto queda como estaba.
     */
    @PostMapping("/v1/lotes/ajustar")
    public ResponseEntity<List<LoteResponse>> ajustarLotes(
            @Valid @RequestBody AjusteLotesRequest request){
        return ResponseEntity.ok(inventarioApi.ajustarLotes(SecurityUtils.getCurrentUserId(), request));
    }

    /** Los lotes que conviene ofrecer en la pantalla de ajuste: sin vencidos ni agotados viejos. */
    @GetMapping("/v1/lotes/ajustables/{idProducto}")
    public ResponseEntity<List<LoteResponse>> getLotesAjustables(@PathVariable UUID idProducto){
        return ResponseEntity.ok(inventarioApi.getLotesAjustables(idProducto));
    }

    @GetMapping("/v1/lotes")
    public ResponseEntity<Page<LoteResponse>> getAllLotes(
            @RequestParam(defaultValue = "0") @Min(value = 0, message = Paginacion.PAGE_MIN) int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = Paginacion.SIZE_MIN)
                @Max(value = Paginacion.MAX_PAGE_SIZE, message = Paginacion.SIZE_MAX) int size){
        return ResponseEntity.ok(inventarioApi.getAll(porVencimiento(page, size)));
    }

    @GetMapping("/v1/lotes/estado/{estado}")
    public ResponseEntity<Page<LoteResponse>> getLotesByEstado(
            @PathVariable String estado,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = Paginacion.PAGE_MIN) int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = Paginacion.SIZE_MIN)
                @Max(value = Paginacion.MAX_PAGE_SIZE, message = Paginacion.SIZE_MAX) int size){
        return ResponseEntity.ok(inventarioApi.getByEstado(estado, porVencimiento(page, size)));
    }

    @GetMapping("/v1/lotes/vencimiento/proximos")
    public ResponseEntity<Page<LoteResponse>> lotesProximos(
            @RequestParam(defaultValue = "0") @Min(value = 0, message = Paginacion.PAGE_MIN) int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = Paginacion.SIZE_MIN)
                @Max(value = Paginacion.MAX_PAGE_SIZE, message = Paginacion.SIZE_MAX) int size){
        return ResponseEntity.ok(inventarioApi.getByEstado("PROXIMO", porVencimiento(page, size)));
    }

    @GetMapping("/v1/lotes/vencimiento/vencidos")
    public ResponseEntity<Page<LoteResponse>> lotesVencidos(
            @RequestParam(defaultValue = "0") @Min(value = 0, message = Paginacion.PAGE_MIN) int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = Paginacion.SIZE_MIN)
                @Max(value = Paginacion.MAX_PAGE_SIZE, message = Paginacion.SIZE_MAX) int size){
        return ResponseEntity.ok(inventarioApi.getByEstado("VENCIDO", porVencimiento(page, size)));
    }

    @GetMapping("/v1/lotes/vencimiento/vigentes")
    public ResponseEntity<Page<LoteResponse>> lotesVigentes(
            @RequestParam(defaultValue = "0") @Min(value = 0, message = Paginacion.PAGE_MIN) int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = Paginacion.SIZE_MIN)
                @Max(value = Paginacion.MAX_PAGE_SIZE, message = Paginacion.SIZE_MAX) int size){
        return ResponseEntity.ok(inventarioApi.getByEstado("VIGENTE", porVencimiento(page, size)));
    }

    /**
     * El orden de los cinco listados de lotes: el que vence antes primero, que es el que hay
     * que mirar. Con el id como desempate, porque los lotes que vencen el mismo día son
     * muchos —una compra entera comparte vencimiento— y sin criterio de desempate las filas se
     * repiten o se saltean al pasar de página.
     */
    private Pageable porVencimiento(int page, int size){
        return PageRequest.of(page, size,
                Sort.by(Sort.Direction.ASC, "fechaVencimiento").and(Sort.by(Sort.Direction.ASC, "id")));
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
