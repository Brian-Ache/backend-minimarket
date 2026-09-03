package com.SolucionesInformaticasBA.minimarket.modules.productos.repository;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.SolucionesInformaticasBA.minimarket.modules.productos.entity.Producto;
import java.util.List;


public interface ProductoRepository extends JpaRepository<Producto,UUID>{
    Producto findByIdAndDeletedAtIsNull(UUID id);

    Producto findByBarcodeAndDeletedAtIsNull(String barcode);

    List<Producto> findByNombreContainingIgnoreCase(String nombre);

    List<Producto> findAllByDeletedAtIsNull();

    List<Producto> findByIdCategoriaAndDeletedAtIsNull(UUID idCategoria);

    List<Producto> findByIdProveedorAndDeletedAtIsNull(UUID idProveedor);

    List<Producto> findByIdCategoriaAndIdProveedorAndDeletedAtIsNull(UUID idCategoria, UUID idProveedor);

    boolean existsByIdAndDeletedAtIsNull(UUID id);

    @Query("SELECT p FROM Producto p WHERE p.deletedAt IS NULL")
    Page<Producto> findAllPaginated(Pageable pageable);

    @Query("SELECT p FROM Producto p WHERE p.deletedAt IS NULL AND LOWER(p.nombre) LIKE LOWER(CONCAT('%', :q, '%'))")
    Page<Producto> findByNombreContainingIgnoreCase(@Param("q") String q, Pageable pageable);

    @Query("SELECT p FROM Producto p WHERE p.deletedAt IS NULL AND p.idCategoria = :idCategoria")
    Page<Producto> findByIdCategoriaAndDeletedAtIsNull(@Param("idCategoria") UUID idCategoria, Pageable pageable);

    @Query("SELECT p FROM Producto p WHERE p.deletedAt IS NULL AND p.idProveedor = :idProveedor")
    Page<Producto> findByIdProveedorAndDeletedAtIsNull(@Param("idProveedor") UUID idProveedor, Pageable pageable);

    @Query("SELECT p FROM Producto p WHERE p.deletedAt IS NULL AND p.idCategoria = :idCategoria AND p.idProveedor = :idProveedor")
    Page<Producto> findByIdCategoriaAndIdProveedorAndDeletedAtIsNull(@Param("idCategoria") UUID idCategoria, @Param("idProveedor") UUID idProveedor, Pageable pageable);

    @Query("SELECT p FROM Producto p WHERE p.deletedAt IS NULL AND LOWER(p.nombre) LIKE LOWER(CONCAT('%', :q, '%')) AND p.idCategoria = :idCategoria AND p.idProveedor = :idProveedor")
    Page<Producto> searchByNombreAndCategoriaAndProveedor(@Param("q") String q, @Param("idCategoria") UUID idCategoria, @Param("idProveedor") UUID idProveedor, Pageable pageable);

    @Query("SELECT p FROM Producto p WHERE p.deletedAt IS NULL AND LOWER(p.nombre) LIKE LOWER(CONCAT('%', :q, '%')) AND p.idCategoria = :idCategoria")
    Page<Producto> searchByNombreAndCategoria(@Param("q") String q, @Param("idCategoria") UUID idCategoria, Pageable pageable);

    @Query("SELECT p FROM Producto p WHERE p.deletedAt IS NULL AND LOWER(p.nombre) LIKE LOWER(CONCAT('%', :q, '%')) AND p.idProveedor = :idProveedor")
    Page<Producto> searchByNombreAndProveedor(@Param("q") String q, @Param("idProveedor") UUID idProveedor, Pageable pageable);
}
