package com.SolucionesInformaticasBA.minimarket.repository;

import com.SolucionesInformaticasBA.minimarket.model.entity.DetalleCompra;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DetalleCompraRepository extends JpaRepository<DetalleCompra, Long> {

    //compraID.compra.id es lo mismo que compraIdCompra, pero con la notación de acceso a propiedades de JPA.
    List<DetalleCompra> findByCompraIdCompra(Long compraId);

    //ProductoID.producto.id es lo mismo que productoIdProducto, pero con la notación de acceso a propiedades de JPA.
    List<DetalleCompra> findByProductoId(Long productoId);
}

