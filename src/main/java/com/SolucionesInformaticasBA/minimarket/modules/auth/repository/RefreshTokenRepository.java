package com.SolucionesInformaticasBA.minimarket.modules.auth.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.SolucionesInformaticasBA.minimarket.modules.auth.entity.RefreshToken;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHashAndIsActiveTrue(String tokenHash);

    // Un usuario puede tener varias sesiones activas a la vez (una por login).
    List<RefreshToken> findByUserIdAndIsActiveTrue(UUID userId);

    // flushAutomatically: confirmPasswordReset deja cambios pendientes sobre otras tablas
    // (usuario y auth_token) que el clear posterior descartaría si no se persisten antes.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE RefreshToken r
               SET r.isActive = false, r.revokedAt = :ahora
             WHERE r.userId = :userId AND r.isActive = true
            """)
    int revokeAllByUserId(@Param("userId") UUID userId, @Param("ahora") LocalDateTime ahora);
}
