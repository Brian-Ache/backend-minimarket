package com.SolucionesInformaticasBA.minimarket.modules.auth.Repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.SolucionesInformaticasBA.minimarket.modules.auth.Entity.RefreshToken;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHashAndIsActiveTrue(String tokenHash);

    Optional<RefreshToken> findByUserIdAndIsActiveTrue(UUID userId);
}
