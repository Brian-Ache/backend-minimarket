package com.SolucionesInformaticasBA.minimarket.modules.auth.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.SolucionesInformaticasBA.minimarket.modules.auth.entity.AuthToken;
import com.SolucionesInformaticasBA.minimarket.modules.auth.enums.TokenType;

public interface AuthTokensRepository extends JpaRepository<AuthToken, UUID> {

    Optional<AuthToken> findByTokenHashAndUsedFalse(String tokenHash);

    // Devuelve lista, no Optional: un usuario puede tener varios tokens sin usar del mismo
    // tipo —pedir el reseteo dos veces basta—, y con Optional la consulta reventaría al
    // encontrar más de una fila.
    List<AuthToken> findByUserIdAndTokenTypeAndUsedFalse(UUID userId, TokenType tokenType);
}
