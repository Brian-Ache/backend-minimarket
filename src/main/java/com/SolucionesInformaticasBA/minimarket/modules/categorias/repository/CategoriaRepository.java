package com.SolucionesInformaticasBA.minimarket.modules.categorias.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.SolucionesInformaticasBA.minimarket.modules.categorias.entity.Categoria;

public interface CategoriaRepository extends JpaRepository<Categoria, UUID> {
    List<Categoria> findAllByDeletedAtIsNull();

    Page<Categoria> findAllByDeletedAtIsNull(Pageable pageable);

    Optional<Categoria> findByIdAndDeletedAtIsNull(UUID id);
    Optional<Categoria> findByNombreAndDeletedAtIsNull(String nombre);
    boolean existsByNombreAndDeletedAtIsNull(String nombre);
    boolean existsByIdAndDeletedAtIsNull(UUID id);
}
