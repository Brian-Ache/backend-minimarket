package com.SolucionesInformaticasBA.minimarket.modules.auth.Repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.SolucionesInformaticasBA.minimarket.modules.auth.Entity.AuthToken;
import com.SolucionesInformaticasBA.minimarket.modules.auth.Enums.TokenType;



public interface AuthTokensRepository extends JpaRepository<AuthToken, UUID> {

    Optional<AuthToken> findByTokenHashAndUsedFalse(String tokenHash);

    Optional<AuthToken> findByUserIdAndTokenTypeAndUsedFalse(UUID userId, TokenType tokenType);
}
