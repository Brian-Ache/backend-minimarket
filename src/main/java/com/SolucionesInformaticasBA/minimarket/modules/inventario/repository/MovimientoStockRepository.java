package com.SolucionesInformaticasBA.minimarket.modules.inventario.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.SolucionesInformaticasBA.minimarket.modules.inventario.entity.MovimientoStock;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.enums.TipoMovimiento;

public interface MovimientoStockRepository extends JpaRepository<MovimientoStock,UUID>{
    List<MovimientoStock> findByIdProductoAndDeletedAtIsNullOrderByCreatedAtDesc(UUID idProducto);

    // Movimientos originados por una venta o compra concreta: base de la reversa al anularla.
    List<MovimientoStock> findByIdReferenciaAndTipoAndDeletedAtIsNull(UUID idReferencia, TipoMovimiento tipo);
}
