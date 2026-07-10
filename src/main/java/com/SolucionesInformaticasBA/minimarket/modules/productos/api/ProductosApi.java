package com.SolucionesInformaticasBA.minimarket.modules.productos.api;

import java.util.List;
import java.util.UUID;

import com.SolucionesInformaticasBA.minimarket.modules.productos.api.dto.ProductoRequest;
import com.SolucionesInformaticasBA.minimarket.modules.productos.api.dto.ProductoResponse;
import com.SolucionesInformaticasBA.minimarket.modules.productos.entity.Producto;

public interface ProductosApi {
    ProductoResponse crear(UUID idUsuario, ProductoRequest request);
    Producto getProductoById(UUID id);
    ProductoResponse getById(UUID id);
    List<ProductoResponse> getAll();
    ProductoResponse getByBarcode(String barcode);
    ProductoResponse update(UUID idProducto, ProductoRequest request);
    void saveEntity(Producto p);
    void delete(UUID id);
}
