package com.SolucionesInformaticasBA.minimarket.modules.proveedores.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.SolucionesInformaticasBA.minimarket.modules.proveedores.entity.Proveedor;

public interface ProveedorRepository extends JpaRepository<Proveedor, UUID> {
    List<Proveedor> findAllByDeletedAtIsNull();

    Page<Proveedor> findAllByDeletedAtIsNull(Pageable pageable);
    Optional<Proveedor> findByIdAndDeletedAtIsNull(UUID id);
    boolean existsByIdAndDeletedAtIsNull(UUID id);
    boolean existsByNombreAndDeletedAtIsNull(String nombre);
}
