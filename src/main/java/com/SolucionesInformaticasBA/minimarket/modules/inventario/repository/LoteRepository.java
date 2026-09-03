package com.SolucionesInformaticasBA.minimarket.modules.inventario.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.SolucionesInformaticasBA.minimarket.modules.inventario.entity.Lote;

public interface LoteRepository extends JpaRepository<Lote,UUID>{
    List<Lote> findByIdProducto(UUID idProducto);

    //buscar lotes por fecha de vencimiento anterior a una fecha dada
    List<Lote> findByFechaVencimientoBefore(LocalDate fecha);

    //buscar lotes por rango de fecha de vencimiento
    List<Lote> findByFechaVencimientoBetween(LocalDate desde, LocalDate hasta);

    //buscar lotes por fecha de vencimiento entre dos fechas dadas y sin fecha de eliminación
    List<Lote> findByFechaVencimientoBetweenAndDeletedAtIsNull(LocalDate inicio, LocalDate fin);

    //buscar lotes por fecha de vencimiento anterior a una fecha dada y sin fecha de eliminación
    List<Lote> findByFechaVencimientoBeforeAndDeletedAtIsNull(LocalDate fecha);

    //buscar lotes por fecha de vencimiento posterior a una fecha dada y sin fecha de eliminación
    List<Lote> findByFechaVencimientoAfterAndDeletedAtIsNull(LocalDate fecha);

    List<Lote> findAllByDeletedAtIsNull();

    List<Lote> findByIdProductoAndDeletedAtIsNullOrderByFechaVencimientoAsc(UUID idProducto);

    // Existencias por producto en una sola consulta: evita un query por producto en el
    // reporte de inventario.
    @Query("""
            SELECT l.idProducto, SUM(l.cantidad) FROM Lote l
             WHERE l.deletedAt IS NULL
             GROUP BY l.idProducto
            """)
    List<Object[]> sumCantidadAgrupadaPorProducto();
}
