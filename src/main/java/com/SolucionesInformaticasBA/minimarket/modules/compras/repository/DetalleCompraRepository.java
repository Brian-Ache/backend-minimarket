package com.SolucionesInformaticasBA.minimarket.modules.compras.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.SolucionesInformaticasBA.minimarket.modules.compras.entity.DetalleCompra;
import java.util.List;


public interface DetalleCompraRepository extends JpaRepository<DetalleCompra, UUID>{
    List<DetalleCompra> findByIdCompraAndDeletedAtIsNull(UUID idCompra);

    // Detalles de varias compras en una sola consulta: evita un query por compra al listar.
    List<DetalleCompra> findByIdCompraInAndDeletedAtIsNull(List<UUID> idsCompra);

    /**
     * La última compra de este producto a cada proveedor: una fila por proveedor, con lo que
     * realmente se pagó. Es el complemento del precio de referencia, que es lo que el
     * proveedor lista.
     *
     * <p>Columnas, en orden: {@code idProveedor}, fecha de la compra, tipo y número de
     * comprobante, y precio unitario de la línea.
     *
     * <p>El filtro por la fecha máxima va en una subconsulta correlacionada y no trayendo todo
     * el historial para descartar en memoria: un producto que se compra hace años tiene cientos
     * de líneas y solo interesa la última de cada proveedor. Se apoya en
     * {@code ix_det_compras_producto} y en {@code ix_compras_proveedor}.
     *
     * <p>Las compras anuladas quedan afuera: si la última compra se anuló, el precio que vale
     * es el de la anterior.
     */
    @Query("""
            SELECT c.idProveedor, c.createdAt, c.tipoComprobante, c.nroComprobante, d.precioUnitario
              FROM DetalleCompra d, Compra c
             WHERE c.id = d.idCompra
               AND d.idProducto = :idProducto
               AND d.deletedAt IS NULL
               AND c.deletedAt IS NULL
               AND c.idProveedor IS NOT NULL
               AND c.createdAt = (SELECT MAX(c2.createdAt)
                                    FROM DetalleCompra d2, Compra c2
                                   WHERE c2.id = d2.idCompra
                                     AND d2.idProducto = :idProducto
                                     AND d2.deletedAt IS NULL
                                     AND c2.deletedAt IS NULL
                                     AND c2.idProveedor = c.idProveedor)
            """)
    List<Object[]> ultimaCompraPorProveedor(@Param("idProducto") UUID idProducto);
}
