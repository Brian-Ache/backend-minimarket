package com.SolucionesInformaticasBA.minimarket.modules.auth.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.SolucionesInformaticasBA.minimarket.modules.auth.Entity.AuthToken;
import com.SolucionesInformaticasBA.minimarket.modules.auth.Entity.RefreshToken;
import com.SolucionesInformaticasBA.minimarket.modules.auth.Enums.TokenType;
import com.SolucionesInformaticasBA.minimarket.modules.auth.Repository.AuthTokensRepository;
import com.SolucionesInformaticasBA.minimarket.modules.auth.Repository.RefreshTokenRepository;
import com.SolucionesInformaticasBA.minimarket.shared.exeption.BadRequestException;
import com.SolucionesInformaticasBA.minimarket.shared.exeption.ResourceNotFoundException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TokenService {

    private static final long VERIFICATION_TOKEN_DURATION_HOURS = 24;
    private static final long PASSWORD_RESET_TOKEN_DURATION_HOURS = 1;
    private static final long REFRESH_TOKEN_DURATION_HOURS = 720;

    private final AuthTokensRepository authTokensRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    public String generateVerificationToken(UUID userId) {
        return createAuthToken(userId, TokenType.VERIFICATION, VERIFICATION_TOKEN_DURATION_HOURS);
    }

    public String generatePasswordResetToken(UUID userId) {
        return createAuthToken(userId, TokenType.PASSWORD_RESET, PASSWORD_RESET_TOKEN_DURATION_HOURS);
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

    @Transactional
    public void revokeRefreshToken(String rawToken) {
        var hashedToken = hashToken(rawToken);
        var refreshToken = refreshTokenRepository.findByTokenHashAndIsActiveTrue(hashedToken)
                .orElseThrow(() -> new BadRequestException("Refresh token inválido"));

        refreshToken.setRevokedAt(LocalDateTime.now());
        refreshToken.setActive(false);
        refreshTokenRepository.save(refreshToken);
    }

    @Transactional
    public void revokeAllUserRefreshTokens(UUID userId) {
        var activeTokens = refreshTokenRepository.findByUserIdAndIsActiveTrue(userId);
        activeTokens.ifPresent(token -> {
            token.setRevokedAt(LocalDateTime.now());
            token.setActive(false);
            refreshTokenRepository.save(token);
        });
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
