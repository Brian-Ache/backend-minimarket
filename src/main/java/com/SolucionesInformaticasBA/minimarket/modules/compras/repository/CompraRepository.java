package com.SolucionesInformaticasBA.minimarket.modules.compras.repository;

import java.util.List;
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
     * Un mismo proveedor no puede tener dos comprobantes con el mismo número. Dos proveedores
     * distintos sí pueden repetirlo: son comprobantes distintos que casualmente coinciden.
     */
    boolean existsByIdProveedorAndNroComprobanteAndDeletedAtIsNull(UUID idProveedor, String nroComprobante);

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
           "AND (:hasta IS NULL OR c.createdAt < :hasta)")
    Page<Compra> findAllFiltered(
        @Param("idProveedor") UUID idProveedor,
        @Param("tipoComprobante") String tipoComprobante,
        @Param("desde") LocalDateTime desde,
        @Param("hasta") LocalDateTime hasta,
        Pageable pageable);
}
