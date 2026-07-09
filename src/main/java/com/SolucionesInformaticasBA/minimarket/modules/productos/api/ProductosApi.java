package com.SolucionesInformaticasBA.minimarket.modules.productos.api;

import java.util.List;
import java.util.UUID;

import com.SolucionesInformaticasBA.minimarket.modules.productos.api.dto.ProductoRequest;
import com.SolucionesInformaticasBA.minimarket.modules.productos.api.dto.ProductoResponse;

public interface ProductosApi {
    ProductoResponse crear(UUID idUsuario, ProductoRequest request);
    ProductoResponse getById(UUID id);
    List<ProductoResponse> getAll();
    ProductoResponse getByBarcode(String barcode);
    ProductoResponse update(UUID idProducto, ProductoRequest request);
    void delete(UUID id);
}
