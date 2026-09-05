package com.SolucionesInformaticasBA.minimarket.modules.productos.controller;

import java.util.List;
import java.util.Optional;
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

import com.SolucionesInformaticasBA.minimarket.modules.productos.api.ProductosApi;
import com.SolucionesInformaticasBA.minimarket.modules.productos.api.dto.PrecioReferenciaRequest;
import com.SolucionesInformaticasBA.minimarket.modules.productos.api.dto.PrecioReferenciaResponse;
import com.SolucionesInformaticasBA.minimarket.modules.productos.api.dto.ProductoRequest;
import com.SolucionesInformaticasBA.minimarket.modules.productos.api.dto.ProductoResponse;
import com.SolucionesInformaticasBA.minimarket.shared.Paginacion;
import com.SolucionesInformaticasBA.minimarket.shared.SecurityUtils;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;


@RestController
@RequestMapping("/api/productos")
@AllArgsConstructor
public class ProductoController {

    private final ProductosApi productosApi;

    @PostMapping("/v1")
    public ResponseEntity<ProductoResponse> crear(
            @Valid @RequestBody ProductoRequest request) {
        return ResponseEntity.ok(productosApi.crear(SecurityUtils.getCurrentUserId(), request));
    }

    @GetMapping("/v1")
    public ResponseEntity<Page<ProductoResponse>> getAll(
            @RequestParam Optional<String> q,
            @RequestParam Optional<UUID> categoria,
            @RequestParam Optional<UUID> proveedor,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = Paginacion.PAGE_MIN) int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = Paginacion.SIZE_MIN)
                @Max(value = Paginacion.MAX_PAGE_SIZE, message = Paginacion.SIZE_MAX) int size) {
                Pageable pageable = pagina(page, size);
                if (q.isPresent()) {
                    if (categoria.isPresent() && proveedor.isPresent()) {
                        return ResponseEntity.ok(productosApi.searchByNombreAndCategoriaAndProveedor(q.get(), categoria.get(), proveedor.get(), pageable));
                    }
                    if (categoria.isPresent()) {
                        return ResponseEntity.ok(productosApi.searchByNombreAndCategoria(q.get(), categoria.get(), pageable));
                    }
                    if (proveedor.isPresent()) {
                        return ResponseEntity.ok(productosApi.searchByNombreAndProveedor(q.get(), proveedor.get(), pageable));
                    }
                    return ResponseEntity.ok(productosApi.search(q.get(), pageable));
                }
                if (categoria.isPresent() && proveedor.isPresent()) {
                    return ResponseEntity.ok(productosApi.getByCategoriaAndProveedor(categoria.get(), proveedor.get(), pageable));
                }
                if (categoria.isPresent()) {
                    return ResponseEntity.ok(productosApi.getByCategoria(categoria.get(), pageable));
                }
                if (proveedor.isPresent()) {
                    return ResponseEntity.ok(productosApi.getByProveedor(proveedor.get(), pageable));
                }
                return ResponseEntity.ok(productosApi.getAll(pageable));
            }

    @GetMapping("/v1/{id}")
    public ResponseEntity<ProductoResponse> getById(@PathVariable UUID id){
        return ResponseEntity.ok(productosApi.getById(id));
    }

    @GetMapping("/v1/search")
    public ResponseEntity<Page<ProductoResponse>> search(
            @RequestParam String q,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = Paginacion.PAGE_MIN) int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = Paginacion.SIZE_MIN)
                @Max(value = Paginacion.MAX_PAGE_SIZE, message = Paginacion.SIZE_MAX) int size) {
        return ResponseEntity.ok(productosApi.search(q, pagina(page, size)));
    }

    @GetMapping("/v1/barcode/{barcode}")
    public ResponseEntity<ProductoResponse> getByBarcode(@PathVariable String barcode){
        return ResponseEntity.ok(productosApi.getByBarcode(barcode));
    }

    @PutMapping("/v1/{id}")
    public ResponseEntity<ProductoResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody ProductoRequest request){
        return ResponseEntity.ok(productosApi.update(id, request));
    }

    @DeleteMapping("/v1/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id){
        productosApi.delete(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Catálogo de precios de referencia del producto: lo que cada proveedor lista por él. Es
     * un dato de consulta, cargado siempre a mano, que no influye en ninguna compra.
     *
     * <p>Para verlo junto con lo que <b>realmente</b> se pagó está
     * {@code GET /api/compras/v1/producto/{idProducto}/proveedores}, que vive en compras
     * porque es el único módulo que ya depende de productos y de proveedores a la vez.
     */
    @GetMapping("/v1/{id}/proveedores")
    public ResponseEntity<List<PrecioReferenciaResponse>> getProveedores(@PathVariable UUID id){
        return ResponseEntity.ok(productosApi.getProveedoresDeProducto(id));
    }

    /** Upsert: el front no tiene por qué saber si la referencia ya existía. */
    @PutMapping("/v1/{id}/proveedores/{idProveedor}")
    public ResponseEntity<PrecioReferenciaResponse> guardarPrecioReferencia(
            @PathVariable UUID id,
            @PathVariable UUID idProveedor,
            @Valid @RequestBody PrecioReferenciaRequest request){
        return ResponseEntity.ok(productosApi.guardarPrecioReferencia(
            id, idProveedor, request.getPrecioReferencia()));
    }

    @DeleteMapping("/v1/{id}/proveedores/{idProveedor}")
    public ResponseEntity<Void> borrarPrecioReferencia(
            @PathVariable UUID id,
            @PathVariable UUID idProveedor){
        productosApi.borrarPrecioReferencia(id, idProveedor);
        return ResponseEntity.noContent().build();
    }

    /**
     * updatedAt se mueve solo —cada compra reescribe costo y precio del producto— y por sí
     * solo no desempata: los productos cargados juntos comparten timestamp. Sin el id como
     * segundo criterio, el orden dentro de un empate lo elige la base y las filas se
     * repetían o se salteaban al pasar de página.
     */
    private Pageable pagina(int page, int size) {
        return PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "updatedAt").and(Sort.by(Sort.Direction.ASC, "id")));
    }
}
