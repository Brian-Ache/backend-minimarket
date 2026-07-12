package com.SolucionesInformaticasBA.minimarket.modules.inventario.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.SolucionesInformaticasBA.minimarket.modules.inventario.entity.Stock;

public interface StockRepository extends JpaRepository<Stock, UUID>{
    Stock findByIdProductoAndDeletedAtIsNull(UUID idProducto);

    Stock findByIdAndDeletedAtIsNull(UUID id);
}
