package com.SolucionesInformaticasBA.minimarket.modules.ventas.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.SolucionesInformaticasBA.minimarket.modules.ventas.entity.DetalleVenta;

import java.util.List;
import java.util.UUID;

public interface DetalleVentaRepository extends JpaRepository<DetalleVenta, UUID> {

    List<DetalleVenta> findByIdVentaAndDeletedAtIsNull(UUID idVenta);

    List<DetalleVenta> findByIdProductoAndDeletedAtIsNull(UUID idProducto);

    // Detalles de varias ventas en una sola consulta: evita un query por venta al listar.
    List<DetalleVenta> findByIdVentaInAndDeletedAtIsNull(List<UUID> idsVenta);
}
