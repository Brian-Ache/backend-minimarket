package com.SolucionesInformaticasBA.minimarket.modules.compras.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.SolucionesInformaticasBA.minimarket.modules.compras.entity.Compra;
import java.util.List;
import java.time.LocalDateTime;


public interface CompraRepository extends JpaRepository<Compra, UUID> {
    Optional<Compra> findByIdAndDeletedAtIsNull(UUID id);

    // Rango semiabierto [desde, hasta), igual que en ventas.
    @Query("""
            SELECT c FROM Compra c
             WHERE c.createdAt >= :desde AND c.createdAt < :hasta
               AND c.deletedAt IS NULL
            """)
    List<Compra> findEnRango(@Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta);

    List<Compra> findByIdUsuarioAndDeletedAtIsNull(UUID idUsuario);
}
