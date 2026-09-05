package com.SolucionesInformaticasBA.minimarket.modules.productos.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.SolucionesInformaticasBA.minimarket.modules.productos.entity.ProductoProveedor;

public interface ProductoProveedorRepository extends JpaRepository<ProductoProveedor, UUID> {

    /** Referencias activas del producto, en orden de precio: la más barata primero. */
    List<ProductoProveedor> findByIdProductoAndDeletedAtIsNullOrderByPrecioReferenciaAsc(UUID idProducto);

    Optional<ProductoProveedor> findByIdProductoAndIdProveedorAndDeletedAtIsNull(
            UUID idProducto, UUID idProveedor);

    /** Para la baja del producto, que se las lleva con él. */
    List<ProductoProveedor> findByIdProductoAndDeletedAtIsNull(UUID idProducto);
}
