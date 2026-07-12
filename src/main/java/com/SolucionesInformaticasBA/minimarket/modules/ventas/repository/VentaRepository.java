package com.SolucionesInformaticasBA.minimarket.modules.ventas.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.SolucionesInformaticasBA.minimarket.modules.ventas.entity.Venta;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;


public interface VentaRepository extends JpaRepository<Venta, UUID> {

    Optional<Venta> findByIdAndDeletedAtIsNull(UUID id);

    Optional<Venta> findByIdAndCobradaFalseAndDeletedAtIsNull(UUID id);

    List<Venta> findByIdUsuarioAndDeletedAtIsNull(UUID idUsuario);

    List<Venta> findByCreatedAtBetweenAndDeletedAtIsNull(LocalDateTime desde, LocalDateTime hasta);

    List<Venta> findByCreatedAtBetweenAndCobradaTrueAndDeletedAtIsNull(LocalDateTime desde, LocalDateTime hasta);
}