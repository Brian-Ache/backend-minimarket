package com.SolucionesInformaticasBA.minimarket.modules.stock.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.SolucionesInformaticasBA.minimarket.modules.stock.entity.Stock;


public interface StockRepository extends JpaRepository<Stock,UUID>{
    Stock findByIdProductoAndDeletedAtIsNull(UUID idProducto);

    Stock findByIdAndDeletedAtIsNull(UUID id);
}
