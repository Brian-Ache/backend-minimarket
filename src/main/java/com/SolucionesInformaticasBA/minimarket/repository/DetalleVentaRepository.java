package com.SolucionesInformaticasBA.minimarket.repository;

import com.SolucionesInformaticasBA.minimarket.model.entity.DetalleVenta;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DetalleVentaRepository extends JpaRepository<DetalleVenta, UUID> {

    List<DetalleVenta> findByVentaId(UUID ventaId);

    List<DetalleVenta> findByProductoId(UUID productoId);
}
