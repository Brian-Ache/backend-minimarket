package com.SolucionesInformaticasBA.minimarket.modules.caja.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

import com.SolucionesInformaticasBA.minimarket.modules.caja.entity.SesionCaja;
import com.SolucionesInformaticasBA.minimarket.modules.caja.enums.EstadoSesion;

public interface SesionCajaRepository extends JpaRepository<SesionCaja, UUID> {
    Optional<SesionCaja> findByIdAndDeletedAtIsNull(UUID id);
    Optional<SesionCaja> findByEstadoAndDeletedAtIsNull(EstadoSesion estado);
    Optional<SesionCaja> findTopByEstadoAndDeletedAtIsNullOrderByCreatedAtDesc(EstadoSesion estado);

    /**
     * La sesión abierta con el lock de la fila tomado (SELECT ... FOR UPDATE). Es la que usa el
     * corte: leía el turno, calculaba el arqueo y lo escribía sin bloquear nada, así que dos
     * cierres simultáneos —dos terminales, o un doble clic— lo cerraban los dos y el segundo
     * pisaba el saldo real, la diferencia y el desglose del primero. El índice único de sesión
     * abierta no alcanza, porque cerrar libera ese lugar en vez de ocuparlo.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT s FROM SesionCaja s
             WHERE s.estado = com.SolucionesInformaticasBA.minimarket.modules.caja.enums.EstadoSesion.ABIERTA
               AND s.deletedAt IS NULL
            """)
    Optional<SesionCaja> findAbiertaParaActualizar();
    Optional<SesionCaja> findTopByOrderByCreatedAtDesc();

    /**
     * Historial de turnos cerrados. El orden va en el {@code Pageable} y no en el nombre del
     * método: una fila por turno cerrado crece para siempre, así que el listado se pagina y el
     * orden tiene que llevar el desempate por id.
     */
    Page<SesionCaja> findByEstadoAndDeletedAtIsNull(EstadoSesion estado, Pageable pageable);

    // Sesiones abiertas dentro de un día, para el saldo inicial del resumen por fecha.
    List<SesionCaja> findByFechaAperturaGreaterThanEqualAndFechaAperturaLessThanAndDeletedAtIsNull(
            LocalDateTime desde, LocalDateTime hasta);
}
