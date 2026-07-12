package com.SolucionesInformaticasBA.minimarket.modules.compras.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.SolucionesInformaticasBA.minimarket.modules.compras.entity.Compra;
import java.util.List;
import java.time.LocalDateTime;


public interface CompraRepository extends JpaRepository<Compra, UUID> {
    Optional<Compra> findByIdAndDeletedAtIsNull(UUID id);

    List<Compra> findByCreatedAtBetweenAndDeletedAtIsNull(LocalDateTime desde, LocalDateTime hasta);

    List<Compra> findByIdUsuarioAndDeletedAtIsNull(UUID idUsuario);
}
