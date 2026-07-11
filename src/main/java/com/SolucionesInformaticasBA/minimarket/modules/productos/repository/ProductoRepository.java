package com.SolucionesInformaticasBA.minimarket.modules.productos.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.SolucionesInformaticasBA.minimarket.modules.productos.entity.Producto;
import java.util.List;


public interface ProductoRepository extends JpaRepository<Producto,UUID>{
    Producto findByIdAndDeletedAtIsNull(UUID id);

    Producto findByBarcodeAndDeletedAtIsNull(String barcode);

    List<Producto> findByNombreContainingIgnoreCase(String nombre);

    List<Producto> findAllByDeletedAtIsNull();

    boolean existsByIdAndDeletedAtIsNull(UUID id);
}
