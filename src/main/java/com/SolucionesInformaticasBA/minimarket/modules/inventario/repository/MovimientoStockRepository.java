package com.SolucionesInformaticasBA.minimarket.modules.inventario.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.SolucionesInformaticasBA.minimarket.modules.inventario.entity.MovimientoStock;

public interface MovimientoStockRepository extends JpaRepository<MovimientoStock,UUID>{
    List<MovimientoStock> findByIdProductoAndDeletedAtIsNullOrderByCreatedAtDesc(UUID idProducto);
}
