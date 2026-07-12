package com.SolucionesInformaticasBA.minimarket.modules.categorias.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.SolucionesInformaticasBA.minimarket.modules.categorias.entity.Categoria;

public interface CategoriaRepository extends JpaRepository<Categoria, UUID> {
    Optional<Categoria> findByIdAndDeletedAtIsNull(UUID id);
    Optional<Categoria> findByNombreAndDeletedAtIsNull(String nombre);
    boolean existsByNombreAndDeletedAtIsNull(String nombre);
    boolean existsByIdAndDeletedAtIsNull(UUID id);
}
