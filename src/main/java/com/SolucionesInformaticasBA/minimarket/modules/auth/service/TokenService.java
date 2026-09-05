package com.SolucionesInformaticasBA.minimarket.modules.auth.service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
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

    private static final long REFRESH_TOKEN_DURATION_HOURS = 720;

    // Pública: el texto del mail necesita informar cuánto dura el enlace.
    public static final long PASSWORD_RESET_TOKEN_DURATION_HOURS = 1;

    // Más larga que el resto: la invitación puede leerse recién al día siguiente.
    public static final long INVITATION_TOKEN_DURATION_HOURS = 72;

    private final AuthTokensRepository authTokensRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    // Generación

    public String generatePasswordResetToken(UUID userId) {
        return createAuthToken(userId, TokenType.PASSWORD_RESET, PASSWORD_RESET_TOKEN_DURATION_HOURS);
    }

    public String generateInvitationToken(UUID userId) {
        return createAuthToken(userId, TokenType.INVITATION, INVITATION_TOKEN_DURATION_HOURS);
    }

    public String generateRefreshToken(UUID userId) {
        String rawToken = generateRandomString();

        RefreshToken refreshToken = RefreshToken.builder()
                .tokenHash(hashToken(rawToken))
                .userId(userId)
                .expiresAt(LocalDateTime.now().plusHours(REFRESH_TOKEN_DURATION_HOURS))
                .isActive(true)
                .build();

        refreshTokenRepository.save(refreshToken);
        return rawToken;
    }

    // Validación

    public AuthToken validateAuthToken(String rawToken, TokenType expectedType) {
        AuthToken authToken = authTokensRepository.findByTokenHashAndUsedFalse(hashToken(rawToken))
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
        RefreshToken refreshToken = refreshTokenRepository.findByTokenHashAndIsActiveTrue(hashToken(rawToken))
                .orElseThrow(() -> new BadRequestException("Refresh token inválido o revocado"));

        if (refreshToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("Refresh token expirado");
        }
        return refreshToken;
    }

    // Invalidación / revocación

    // Invalida tokens sin usar de un tipo antes de emitir uno nuevo (p. ej. reenvío de invitación).
    @Transactional
    public void invalidateAuthTokens(UUID userId, TokenType tokenType) {
        List<AuthToken> tokens = authTokensRepository.findByUserIdAndTokenTypeAndUsedFalse(userId, tokenType);
        tokens.forEach(token -> token.setUsed(true));
        authTokensRepository.saveAll(tokens);
    }

    @Transactional
    public void markAuthTokenAsUsed(UUID tokenId) {
        AuthToken token = authTokensRepository.findById(tokenId)
                .orElseThrow(() -> new ResourceNotFoundException("Token no encontrado"));
        token.setUsed(true);
        authTokensRepository.save(token);
    }

    // Idempotente a propósito: revocar un token ya inactivo o inexistente no es un error.
    @Transactional
    public void revokeRefreshToken(String rawToken) {
        refreshTokenRepository.findByTokenHashAndIsActiveTrue(hashToken(rawToken))
                .ifPresent(refreshToken -> {
                    refreshToken.setRevokedAt(LocalDateTime.now());
                    refreshToken.setActive(false);
                    refreshTokenRepository.save(refreshToken);
                });
    }

    // Cierra todas las sesiones del usuario (cambio de contraseña, baja, etc.).
    @Transactional
    public int revokeAllUserRefreshTokens(UUID userId) {
        return refreshTokenRepository.revokeAllByUserId(userId, LocalDateTime.now());
    }

    // Helpers

    private String createAuthToken(UUID userId, TokenType tokenType, long durationHours) {
        String rawToken = generateRandomString();

        AuthToken authToken = AuthToken.builder()
                .tokenType(tokenType)
                .tokenHash(hashToken(rawToken))
                .userId(userId)
                .expiresAt(LocalDateTime.now().plusHours(durationHours))
                .used(false)
                .build();

        authTokensRepository.save(authToken);
        return rawToken;
    }

    private String generateRandomString() {
        byte[] randomBytes = new byte[32];
        new SecureRandom().nextBytes(randomBytes);
        return HexFormat.of().formatHex(randomBytes);
    }

    public String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes()));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 no disponible", e);
        }
    }
}