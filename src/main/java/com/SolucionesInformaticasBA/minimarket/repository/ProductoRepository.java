package com.SolucionesInformaticasBA.minimarket.repository;

import com.SolucionesInformaticasBA.minimarket.model.entity.Producto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductoRepository extends JpaRepository<Producto, Long> {

    Optional<Producto> findByBarcode(String barcode);
    
    List<Producto> findByNombreContainingIgnoreCase(String nombre);
}
