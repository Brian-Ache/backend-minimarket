package com.SolucionesInformaticasBA.minimarket.modules.caja.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.SolucionesInformaticasBA.minimarket.modules.caja.entity.MovimientoCaja;
import com.SolucionesInformaticasBA.minimarket.modules.caja.enums.TipoMovimientoCaja;

public interface MovimientoCajaRepository extends JpaRepository<MovimientoCaja, UUID> {
    List<MovimientoCaja> findByIdSesionAndDeletedAtIsNull(UUID idSesion);
    // Rango semiabierto [desde, hasta), igual que en ventas y compras.
    @Query("""
            SELECT m FROM MovimientoCaja m
             WHERE m.createdAt >= :desde AND m.createdAt < :hasta
               AND m.deletedAt IS NULL
            """)
    List<MovimientoCaja> findEnRango(@Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta);
    List<MovimientoCaja> findByIdSesionAndTipoAndOrigenAndDeletedAtIsNull(UUID idSesion, TipoMovimientoCaja tipo, String origen);
    List<MovimientoCaja> findByIdSesionAndOrigenAndDeletedAtIsNull(UUID idSesion, String origen);
    List<MovimientoCaja> findByIdSesionAndTipoAndDeletedAtIsNull(UUID idSesion, TipoMovimientoCaja tipo);
}
