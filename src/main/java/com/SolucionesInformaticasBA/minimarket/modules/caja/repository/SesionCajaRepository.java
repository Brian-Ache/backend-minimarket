package com.SolucionesInformaticasBA.minimarket.modules.caja.repository;

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
}
