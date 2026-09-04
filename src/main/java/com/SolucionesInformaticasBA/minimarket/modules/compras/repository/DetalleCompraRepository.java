package com.SolucionesInformaticasBA.minimarket.modules.compras.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.SolucionesInformaticasBA.minimarket.modules.compras.entity.DetalleCompra;
import java.util.List;


public interface DetalleCompraRepository extends JpaRepository<DetalleCompra, UUID>{
    List<DetalleCompra> findByIdCompraAndDeletedAtIsNull(UUID idCompra);

    // Detalles de varias compras en una sola consulta: evita un query por compra al listar.
    List<DetalleCompra> findByIdCompraInAndDeletedAtIsNull(List<UUID> idsCompra);
}
