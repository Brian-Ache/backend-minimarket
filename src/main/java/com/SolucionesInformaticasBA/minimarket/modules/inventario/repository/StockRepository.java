package com.SolucionesInformaticasBA.minimarket.modules.inventario.repository;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.SolucionesInformaticasBA.minimarket.modules.inventario.entity.Stock;

import jakarta.persistence.LockModeType;

public interface StockRepository extends JpaRepository<Stock, UUID>{
    Optional<Stock> findByIdProductoAndDeletedAtIsNull(UUID idProducto);

    /**
     * Igual que la anterior pero tomando el lock de la fila (SELECT ... FOR UPDATE). Es la que
     * va antes de escribir la cantidad: leer y reescribir sin bloqueo hacía que dos ventas
     * simultáneas del mismo producto partieran del mismo valor y una pisara a la otra, con lo
     * que se vendía de más y el stock quedaba alto. Exige transacción abierta.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Stock s WHERE s.idProducto = :idProducto AND s.deletedAt IS NULL")
    Optional<Stock> findByIdProductoParaActualizar(@Param("idProducto") UUID idProducto);

    @Query("SELECT s.idProducto, s.cantidad FROM Stock s WHERE s.deletedAt IS NULL")
    List<Object[]> cantidadesPorProducto();

    List<Stock> findByIdProductoInAndDeletedAtIsNull(List<UUID> idProductos);
}
