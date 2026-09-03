package com.SolucionesInformaticasBA.minimarket.modules.inventario.repository;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.SolucionesInformaticasBA.minimarket.modules.inventario.entity.Stock;

public interface StockRepository extends JpaRepository<Stock, UUID>{
    Optional<Stock> findByIdProductoAndDeletedAtIsNull(UUID idProducto);

    Optional<Stock> findByIdAndDeletedAtIsNull(UUID id);

    @Query("SELECT s.idProducto, s.cantidad FROM Stock s WHERE s.deletedAt IS NULL")
    List<Object[]> cantidadesPorProducto();

    List<Stock> findByIdProductoInAndDeletedAtIsNull(List<UUID> idProductos);
}
