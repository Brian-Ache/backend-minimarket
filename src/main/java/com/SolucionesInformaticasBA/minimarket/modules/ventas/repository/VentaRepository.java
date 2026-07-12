package com.SolucionesInformaticasBA.minimarket.modules.ventas.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.SolucionesInformaticasBA.minimarket.modules.ventas.entity.Venta;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;


public interface VentaRepository extends JpaRepository<Venta, UUID> {

    List<Venta> findByFechaBetween(LocalDateTime desde, LocalDateTime hasta);
    List<Venta> findByUsuarioId(Long usuarioId);

    

    @Query("""
    SELECT v FROM Venta v
    LEFT JOIN FETCH v.detalles d
    LEFT JOIN FETCH d.producto
    WHERE v.id = :id
    """)
    Optional<Venta> findByIdConDetalles(UUID id);

    @Query("""
    SELECT DISTINCT v FROM Venta v
    LEFT JOIN FETCH v.detalles d
    LEFT JOIN FETCH d.producto
    """)
    List<Venta> findAllConDetalles();
}