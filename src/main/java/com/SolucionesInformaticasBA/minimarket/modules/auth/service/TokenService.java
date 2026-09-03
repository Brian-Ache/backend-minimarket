package com.SolucionesInformaticasBA.minimarket.modules.auth.service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.SolucionesInformaticasBA.minimarket.modules.auth.entity.AuthToken;
import com.SolucionesInformaticasBA.minimarket.modules.auth.entity.RefreshToken;
import com.SolucionesInformaticasBA.minimarket.modules.auth.enums.TokenType;
import com.SolucionesInformaticasBA.minimarket.modules.auth.repository.AuthTokensRepository;
import com.SolucionesInformaticasBA.minimarket.modules.auth.repository.RefreshTokenRepository;
import com.SolucionesInformaticasBA.minimarket.shared.exeption.BadRequestException;
import com.SolucionesInformaticasBA.minimarket.shared.exeption.ResourceNotFoundException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TokenService {

    private static final long VERIFICATION_TOKEN_DURATION_HOURS = 24;
    private static final long REFRESH_TOKEN_DURATION_HOURS = 720;

    // Públicas porque el texto del mail avisa cuánto dura el enlace, y ese dato tiene que salir
    // de la misma constante que lo calcula.
    public static final long PASSWORD_RESET_TOKEN_DURATION_HOURS = 1;

    /**
     * Más larga que las demás: la invitación le llega a alguien que no está esperando el mail y
     * que puede leerlo recién al otro día. Tres días es margen suficiente sin dejar la puerta
     * abierta indefinidamente; vencida, el administrador reenvía.
     */
    public static final long INVITATION_TOKEN_DURATION_HOURS = 72;

    private final AuthTokensRepository authTokensRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    public String generateVerificationToken(UUID userId) {
        return createAuthToken(userId, TokenType.VERIFICATION, VERIFICATION_TOKEN_DURATION_HOURS);
    }

    public String generatePasswordResetToken(UUID userId) {
        return createAuthToken(userId, TokenType.PASSWORD_RESET, PASSWORD_RESET_TOKEN_DURATION_HOURS);
    }

    public String generateInvitationToken(UUID userId) {
        return createAuthToken(userId, TokenType.INVITATION, INVITATION_TOKEN_DURATION_HOURS);
    }

    /**
     * Invalida los tokens sin usar de un tipo. Se llama antes de emitir uno nuevo: si un
     * administrador reenvía la invitación, el enlace anterior tiene que dejar de servir, porque
     * si no cada reenvío deja otra puerta abierta hasta que expire.
     */
    @Transactional
    public void invalidateAuthTokens(UUID userId, TokenType tokenType) {
        var tokens = authTokensRepository.findByUserIdAndTokenTypeAndUsedFalse(userId, tokenType);
        tokens.forEach(token -> token.setUsed(true));
        authTokensRepository.saveAll(tokens);
    }

    public String generateRefreshToken(UUID userId) {
        var rawToken = generateRandomString();
        var hashedToken = hashToken(rawToken);
        var expiresAt = LocalDateTime.now().plusHours(REFRESH_TOKEN_DURATION_HOURS);

        var refreshToken = RefreshToken.builder()
                .tokenHash(hashedToken)
                .userId(userId)
                .expiresAt(expiresAt)
                .isActive(true)
                .build();

        refreshTokenRepository.save(refreshToken);
        return rawToken;
    }

    public AuthToken validateAuthToken(String rawToken, TokenType expectedType) {
        var hashedToken = hashToken(rawToken);

        var authToken = authTokensRepository.findByTokenHashAndUsedFalse(hashedToken)
                .orElseThrow(() -> new BadRequestException("Token inválido o ya utilizado"));

        if (authToken.getTokenType() != expectedType) {
            throw new BadRequestException("Tipo de token incorrecto");
        }

        if (authToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("Token expirado");
        }

        return authToken;
    }

    public RefreshToken validateRefreshToken(String rawToken) {
        var hashedToken = hashToken(rawToken);

        var refreshToken = refreshTokenRepository.findByTokenHashAndIsActiveTrue(hashedToken)
                .orElseThrow(() -> new BadRequestException("Refresh token inválido o revocado"));

        if (refreshToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("Refresh token expirado");
        }

        return refreshToken;
    }

    @Transactional
    public void markAuthTokenAsUsed(UUID tokenId) {
        var token = authTokensRepository.findById(tokenId)
                .orElseThrow(() -> new ResourceNotFoundException("Token no encontrado"));
        token.setUsed(true);
        authTokensRepository.save(token);
    }

    /**
     * Revoca una sesión. Es idempotente a propósito: desloguear un token ya revocado,
     * expirado o inexistente no es un error para el cliente.
     */
    @Transactional
    public void revokeRefreshToken(String rawToken) {
        refreshTokenRepository.findByTokenHashAndIsActiveTrue(hashToken(rawToken))
                .ifPresent(refreshToken -> {
                    refreshToken.setRevokedAt(LocalDateTime.now());
                    refreshToken.setActive(false);
                    refreshTokenRepository.save(refreshToken);
                });
    }

    /** Cierra todas las sesiones del usuario (cambio de contraseña, baja, etc.). */
    @Transactional
    public int revokeAllUserRefreshTokens(UUID userId) {
        return refreshTokenRepository.revokeAllByUserId(userId, LocalDateTime.now());
    }

    private String createAuthToken(UUID userId, TokenType tokenType, long durationHours) {
        var rawToken = generateRandomString();
        var hashedToken = hashToken(rawToken);
        var expiresAt = LocalDateTime.now().plusHours(durationHours);

        var authToken = AuthToken.builder()
                .tokenType(tokenType)
                .tokenHash(hashedToken)
                .userId(userId)
                .expiresAt(expiresAt)
                .used(false)
                .build();

        authTokensRepository.save(authToken);
        return rawToken;
    }

    private String generateRandomString() {
        var randomBytes = new byte[32];
        new SecureRandom().nextBytes(randomBytes);
        return HexFormat.of().formatHex(randomBytes);
    }

    public String hashToken(String rawToken) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            var hashBytes = digest.digest(rawToken.getBytes());
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 no disponible", e);
        }
    }
}
