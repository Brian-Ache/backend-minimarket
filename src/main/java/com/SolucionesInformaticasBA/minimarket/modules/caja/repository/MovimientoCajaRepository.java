package com.SolucionesInformaticasBA.minimarket.modules.caja.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.SolucionesInformaticasBA.minimarket.modules.caja.entity.MovimientoCaja;
import com.SolucionesInformaticasBA.minimarket.modules.caja.enums.TipoMovimientoCaja;

public interface MovimientoCajaRepository extends JpaRepository<MovimientoCaja, UUID> {
    List<MovimientoCaja> findByIdSesionAndDeletedAtIsNull(UUID idSesion);
    List<MovimientoCaja> findByCreatedAtBetweenAndDeletedAtIsNull(LocalDateTime desde, LocalDateTime hasta);
    List<MovimientoCaja> findByIdSesionAndTipoAndOrigenAndDeletedAtIsNull(UUID idSesion, TipoMovimientoCaja tipo, String origen);
    List<MovimientoCaja> findByIdSesionAndOrigenAndDeletedAtIsNull(UUID idSesion, String origen);
    List<MovimientoCaja> findByIdSesionAndTipoAndDeletedAtIsNull(UUID idSesion, TipoMovimientoCaja tipo);
}
