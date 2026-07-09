package com.SolucionesInformaticasBA.minimarket.repository;

import com.SolucionesInformaticasBA.minimarket.model.entity.MovimientoStock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MovimientoStockRepository extends JpaRepository<MovimientoStock, UUID> {

    List<MovimientoStock> findByProductoIdOrderByFechaDesc(UUID productoId);

    List<MovimientoStock> findByIdProductoOrderByCreatedAtDesc(UUID idProducto);
}