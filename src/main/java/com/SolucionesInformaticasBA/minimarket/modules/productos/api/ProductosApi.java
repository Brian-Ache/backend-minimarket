package com.SolucionesInformaticasBA.minimarket.modules.productos.api;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.SolucionesInformaticasBA.minimarket.modules.productos.api.dto.ProductoRequest;
import com.SolucionesInformaticasBA.minimarket.modules.productos.api.dto.ProductoResponse;

public interface ProductosApi {
    ProductoResponse crear(UUID idUsuario, ProductoRequest request);
    ProductoResponse getById(UUID id);

    /**
     * Nombres de los productos pedidos, para listados de otros módulos que muestran el nombre
     * junto a sus propios datos. Evita traerse el catálogo entero solo para eso.
     *
     * <p>Incluye los productos dados de baja: un lote o un movimiento viejo tiene que poder
     * seguir diciendo de qué producto era. El valor puede ser null si la fila no tiene nombre
     * cargado, y los ids que no existen no aparecen en el mapa.
     */
    Map<UUID, String> getNombresPorId(Collection<UUID> ids);

    /**
     * Barcodes de los productos pedidos, con las mismas reglas que {@link #getNombresPorId}:
     * en una sola consulta, incluyendo los productos dados de baja, con valor null si la fila
     * no tiene barcode cargado y sin entrada para los ids que no existen.
     */
    Map<UUID, String> getBarcodesPorId(Collection<UUID> ids);
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
