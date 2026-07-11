package com.SolucionesInformaticasBA.minimarket.modules.compras.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.SolucionesInformaticasBA.minimarket.modules.compras.entity.Compra;
import java.util.List;
import java.time.LocalDateTime;


public interface CompraRepository extends JpaRepository<Compra, UUID> {
    List<Compra> findByCreatedAtBetweenAndDeletedAtIsNull(LocalDateTime desde, LocalDateTime hsta);

    List<Compra> findByIdUsuarioAndDeletedAtIsNull(UUID idUsuario);
}
