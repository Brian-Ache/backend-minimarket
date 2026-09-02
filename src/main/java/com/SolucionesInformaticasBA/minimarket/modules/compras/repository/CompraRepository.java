package com.SolucionesInformaticasBA.minimarket.modules.compras.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.SolucionesInformaticasBA.minimarket.modules.compras.entity.Compra;
import java.time.LocalDateTime;


public interface CompraRepository extends JpaRepository<Compra, UUID> {
    Optional<Compra> findByIdAndDeletedAtIsNull(UUID id);

    /**
     * Query unificado de compras con filtros opcionales.
     *
     * Cada condición usa ":param IS NULL OR campo = :param".
     * Cuando el parámetro es null, la condición se evalúa como TRUE
     * y no filtra nada (efectivamente se ignora ese filtro).
     *
     * Ejemplo: si idProveedor es null y tipoComprobante es "FACTURA",
     * el query retorna todas las compras de tipo FACTURA sin importar el proveedor.
     *
     * El ordenamiento viene dado por Pageable (sort por createdAt o total).
     */
    @Query("SELECT c FROM Compra c WHERE c.deletedAt IS NULL " +
           "AND (:idProveedor IS NULL OR c.idProveedor = :idProveedor) " +
           "AND (:tipoComprobante IS NULL OR c.tipoComprobante = :tipoComprobante) " +
           "AND (:desde IS NULL OR c.createdAt >= :desde) " +
           "AND (:hasta IS NULL OR c.createdAt <= :hasta)")
    Page<Compra> findAllFiltered(
        @Param("idProveedor") UUID idProveedor,
        @Param("tipoComprobante") String tipoComprobante,
        @Param("desde") LocalDateTime desde,
        @Param("hasta") LocalDateTime hasta,
        Pageable pageable);

    // MÉTODOS COMENTADOS: Se reemplazaron por findAllFiltered() que cubre todos los casos
    // con un solo query parametrizado. Se mantienen comentados por si en el futuro
    // se necesitan endpoints dedicados (ej: historial por un usuario específico).

    // @Query("SELECT c FROM Compra c WHERE c.deletedAt IS NULL ORDER BY c.createdAt DESC")
    // Page<Compra> findAllPaginated(Pageable pageable);

    // @Query("SELECT c FROM Compra c WHERE c.deletedAt IS NULL AND c.createdAt BETWEEN :desde AND :hasta ORDER BY c.createdAt DESC")
    // Page<Compra> findByCreatedAtBetweenAndDeletedAtIsNull(LocalDateTime desde, LocalDateTime hasta, Pageable pageable);

    // @Query("SELECT c FROM Compra c WHERE c.deletedAt IS NULL AND c.idUsuario = :idUsuario ORDER BY c.createdAt DESC")
    // Page<Compra> findByIdUsuarioAndDeletedAtIsNull(UUID idUsuario, Pageable pageable);
}
