package com.SolucionesInformaticasBA.minimarket.modules.ventas.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.SolucionesInformaticasBA.minimarket.modules.ventas.entity.DetalleVenta;

import java.util.List;
import java.util.UUID;

public interface DetalleVentaRepository extends JpaRepository<DetalleVenta, UUID> {

    List<DetalleVenta> findByVentaId(UUID ventaId);

    List<DetalleVenta> findByProductoId(UUID productoId);
}
