package com.SolucionesInformaticasBA.minimarket.modules.productos.api;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.SolucionesInformaticasBA.minimarket.modules.productos.api.dto.ProductoRequest;
import com.SolucionesInformaticasBA.minimarket.modules.productos.api.dto.ProductoResponse;

public interface ProductosApi {
    ProductoResponse crear(UUID idUsuario, ProductoRequest request);
    ProductoResponse getById(UUID id);
    Page<ProductoResponse> getAll(Pageable pageable);
    Page<ProductoResponse> getByCategoria(UUID idCategoria, Pageable pageable);
    Page<ProductoResponse> getByProveedor(UUID idProveedor, Pageable pageable);
    Page<ProductoResponse> getByCategoriaAndProveedor(UUID idCategoria, UUID idProveedor, Pageable pageable);
    ProductoResponse getByBarcode(String barcode);
    Page<ProductoResponse> search(String q, Pageable pageable);
    Page<ProductoResponse> searchByNombreAndCategoria(String q, UUID idCategoria, Pageable pageable);
    Page<ProductoResponse> searchByNombreAndProveedor(String q, UUID idProveedor, Pageable pageable);
    Page<ProductoResponse> searchByNombreAndCategoriaAndProveedor(String q, UUID idCategoria, UUID idProveedor, Pageable pageable);
    ProductoResponse update(UUID idProducto, ProductoRequest request);
    void delete(UUID id);
    boolean existsById(UUID id);
}
