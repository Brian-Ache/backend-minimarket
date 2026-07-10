package com.SolucionesInformaticasBA.minimarket.modules.stock.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.SolucionesInformaticasBA.minimarket.modules.stock.entity.MovimientoStock;
import java.util.List;


public interface MovimientoStockRepository extends JpaRepository<MovimientoStock,UUID>{

    List<MovimientoStock> findByIdProductoAndDeletedAtIsNullOrderByCreatedAtDesc(UUID idProducto);
}
