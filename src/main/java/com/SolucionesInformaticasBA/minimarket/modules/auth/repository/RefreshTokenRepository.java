package com.SolucionesInformaticasBA.minimarket.modules.auth.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.SolucionesInformaticasBA.minimarket.modules.auth.entity.RefreshToken;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHashAndIsActiveTrue(String tokenHash);

    Optional<RefreshToken> findByUserIdAndIsActiveTrue(UUID userId);
}
