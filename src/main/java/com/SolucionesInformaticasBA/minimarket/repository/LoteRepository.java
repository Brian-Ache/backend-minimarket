package com.SolucionesInformaticasBA.minimarket.repository;
import com.SolucionesInformaticasBA.minimarket.model.entity.Lote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface LoteRepository extends JpaRepository<Lote, UUID> {

    List<Lote> findByProductoId(Long productoId);

    //buscar lotes por fecha de vencimiento anterior a una fecha dada
    List<Lote> findByFechaVencimientoBefore(LocalDate fecha);

    //buscar lotes por rango de fecha de vencimiento
    List<Lote> findByFechaVencimientoBetween(LocalDate desde, LocalDate hasta);

    //buscar lotes por fecha de vencimiento entre dos fechas dadas y sin fecha de eliminación
    List<Lote> findByFechaVencimientoBetweenAndFechaEliminacionIsNull(LocalDate inicio, LocalDate fin);

    //buscar lotes por fecha de vencimiento anterior a una fecha dada y sin fecha de eliminación
    List<Lote> findByFechaVencimientoBeforeAndFechaEliminacionIsNull(LocalDate fecha);

    //buscar lotes por fecha de vencimiento posterior a una fecha dada y sin fecha de eliminación
    List<Lote> findByFechaVencimientoAfterAndFechaEliminacionIsNull(LocalDate fecha);
}
