package com.SolucionesInformaticasBA.minimarket.modules.ventas.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.SolucionesInformaticasBA.minimarket.modules.ventas.entity.Venta;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;


public interface VentaRepository extends JpaRepository<Venta, UUID> {

    Optional<Venta> findByIdAndDeletedAtIsNull(UUID id);

    Optional<Venta> findByIdAndCobradaFalseAndDeletedAtIsNull(UUID id);

    List<Venta> findByIdUsuarioAndDeletedAtIsNull(UUID idUsuario);

    // Rango semiabierto [desde, hasta): Between es inclusivo en ambos extremos, así que una
    // venta justo en el límite se contaba en dos períodos consecutivos.
    @Query("""
            SELECT v FROM Venta v
             WHERE v.createdAt >= :desde AND v.createdAt < :hasta
               AND v.deletedAt IS NULL
            """)
    List<Venta> findEnRango(@Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta);

    /**
     * Ventas efectivamente cobradas en el período, filtradas por <b>fecha de cobro</b>.
     * Todo reporte de dinero usa esta: una venta creada ayer y cobrada hoy es plata de hoy.
     */
    @Query("""
            SELECT v FROM Venta v
             WHERE v.fechaCobro >= :desde AND v.fechaCobro < :hasta
               AND v.cobrada = true AND v.deletedAt IS NULL
            """)
    List<Venta> findCobradasEnRango(@Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta);

    List<Venta> findByIdSesionAndCobradaTrueAndDeletedAtIsNull(UUID idSesion);
}