package com.SolucionesInformaticasBA.minimarket.modules.auth.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.SolucionesInformaticasBA.minimarket.modules.auth.entity.AuthToken;
import com.SolucionesInformaticasBA.minimarket.modules.auth.enums.TokenType;



public interface AuthTokensRepository extends JpaRepository<AuthToken, UUID> {

    Optional<AuthToken> findByTokenHashAndUsedFalse(String tokenHash);

    Optional<AuthToken> findByUserIdAndTokenTypeAndUsedFalse(UUID userId, TokenType tokenType);
}
