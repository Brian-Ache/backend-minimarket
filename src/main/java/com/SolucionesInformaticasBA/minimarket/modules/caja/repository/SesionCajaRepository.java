package com.SolucionesInformaticasBA.minimarket.modules.caja.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.SolucionesInformaticasBA.minimarket.modules.caja.entity.SesionCaja;
import com.SolucionesInformaticasBA.minimarket.modules.caja.enums.EstadoSesion;

public interface SesionCajaRepository extends JpaRepository<SesionCaja, UUID> {
    Optional<SesionCaja> findByIdAndDeletedAtIsNull(UUID id);
    Optional<SesionCaja> findByEstadoAndDeletedAtIsNull(EstadoSesion estado);
    Optional<SesionCaja> findTopByEstadoAndDeletedAtIsNullOrderByCreatedAtDesc(EstadoSesion estado);
    Optional<SesionCaja> findTopByOrderByCreatedAtDesc();

    List<SesionCaja> findByEstadoAndDeletedAtIsNullOrderByFechaCierreDesc(EstadoSesion estado);

    // Sesiones abiertas dentro de un día, para el saldo inicial del resumen por fecha.
    List<SesionCaja> findByFechaAperturaGreaterThanEqualAndFechaAperturaLessThanAndDeletedAtIsNull(
            LocalDateTime desde, LocalDateTime hasta);
}
