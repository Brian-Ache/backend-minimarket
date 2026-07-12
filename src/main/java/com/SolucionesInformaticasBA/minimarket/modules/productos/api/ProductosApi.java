package com.SolucionesInformaticasBA.minimarket.modules.productos.api;

import java.util.List;
import java.util.UUID;

import com.SolucionesInformaticasBA.minimarket.modules.productos.api.dto.ProductoRequest;
import com.SolucionesInformaticasBA.minimarket.modules.productos.api.dto.ProductoResponse;

public interface ProductosApi {
    ProductoResponse crear(UUID idUsuario, ProductoRequest request);
    ProductoResponse getById(UUID id);
    List<ProductoResponse> getAll();
    List<ProductoResponse> getByCategoria(UUID idCategoria);
    List<ProductoResponse> getByProveedor(UUID idProveedor);
    List<ProductoResponse> getByCategoriaAndProveedor(UUID idCategoria, UUID idProveedor);
    ProductoResponse getByBarcode(String barcode);
    List<ProductoResponse> search(String q);
    ProductoResponse update(UUID idProducto, ProductoRequest request);
    void delete(UUID id);
    boolean existsById(UUID id);
}
