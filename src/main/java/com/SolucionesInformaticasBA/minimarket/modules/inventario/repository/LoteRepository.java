package com.SolucionesInformaticasBA.minimarket.modules.inventario.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.SolucionesInformaticasBA.minimarket.modules.inventario.entity.Lote;

import jakarta.persistence.LockModeType;

public interface LoteRepository extends JpaRepository<Lote,UUID>{
    //buscar lotes por fecha de vencimiento entre dos fechas dadas y sin fecha de eliminación
    List<Lote> findByFechaVencimientoBetweenAndDeletedAtIsNull(LocalDate inicio, LocalDate fin);

    //buscar lotes por fecha de vencimiento anterior a una fecha dada y sin fecha de eliminación
    List<Lote> findByFechaVencimientoBeforeAndDeletedAtIsNull(LocalDate fecha);

    //buscar lotes por fecha de vencimiento posterior a una fecha dada y sin fecha de eliminación
    List<Lote> findByFechaVencimientoAfterAndDeletedAtIsNull(LocalDate fecha);

    List<Lote> findAllByDeletedAtIsNull();

    List<Lote> findByFechaVencimientoIsNullAndDeletedAtIsNull();

    /**
     * Lotes activos del producto con el lock de cada fila tomado (SELECT ... FOR UPDATE), en
     * orden de vencimiento. Sin bloqueo, dos ventas simultáneas del mismo producto descontaban
     * las dos sobre la misma cantidad leída y el lote terminaba con más unidades de las que
     * realmente quedaban. Exige transacción abierta.
     *
     * <p>Es la <b>única</b> puerta para bloquear los lotes de un producto, y por eso el orden
     * es parte del contrato: el desempate por id evita que dos lotes con el mismo vencimiento
     * se tomen en orden distinto según la consulta. Todas las transacciones bloquean los lotes
     * de un producto en esta misma secuencia, así que no puede haber ciclo entre ellas.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT l FROM Lote l WHERE l.idProducto = :idProducto AND l.deletedAt IS NULL"
        + " ORDER BY l.fechaVencimiento ASC, l.id ASC")
    List<Lote> findParaDescuentoFifo(@Param("idProducto") UUID idProducto);

    /** Un lote con el lock de la fila tomado, para revertir una venta o una compra. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT l FROM Lote l WHERE l.id = :id")
    Optional<Lote> findByIdParaActualizar(@Param("id") UUID id);

    // Existencias por producto en una sola consulta: evita un query por producto en el
    // reporte de inventario.
    @Query("""
            SELECT l.idProducto, SUM(l.cantidad) FROM Lote l
             WHERE l.deletedAt IS NULL
             GROUP BY l.idProducto
            """)
    List<Object[]> sumCantidadAgrupadaPorProducto();
}
