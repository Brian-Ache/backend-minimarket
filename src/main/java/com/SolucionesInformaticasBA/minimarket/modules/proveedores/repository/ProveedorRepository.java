package com.SolucionesInformaticasBA.minimarket.modules.proveedores.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.SolucionesInformaticasBA.minimarket.modules.proveedores.entity.Proveedor;

public interface ProveedorRepository extends JpaRepository<Proveedor, UUID> {
    Optional<Proveedor> findByIdAndDeletedAtIsNull(UUID id);
    boolean existsByIdAndDeletedAtIsNull(UUID id);
}
