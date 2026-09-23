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
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class TokenService {

    private static final long REFRESH_TOKEN_DURATION_HOURS = 720;

    /** Mismo texto para todo rechazo de refresh: el motivo real no se cuenta. */
    private static final String RECHAZO_REFRESH = "Refresh token inválido o revocado";

    /**
     * Cuánto se le concede a un refresh token ya rotado antes de tratarlo como robado. Cubre el
     * reintento del cliente cuando la respuesta de la rotación se perdió; un ladrón, en cambio,
     * usa la copia cuando llega, no dentro de los segundos.
     */
    private static final long GRACIA_REUSO_SEGUNDOS = 30;

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

    /**
     * Valida el refresh token de una renovación y, de paso, detecta el reuso: un token que ya se
     * rotó y vuelve a aparecer es la señal de que alguien tiene una copia.
     *
     * <p>Busca sin filtrar por {@code isActive} porque esa distinción es todo el punto. Antes la
     * consulta pedía solo los activos, así que un token robado y ya rotado daba vacío igual que
     * uno inventado, y la rotación no servía de nada: el ladrón simplemente no volvía a usarlo.
     */
    public RefreshToken validateRefreshToken(String rawToken) {
        RefreshToken refreshToken = refreshTokenRepository.findByTokenHash(hashToken(rawToken))
                .orElseThrow(() -> new BadRequestException(RECHAZO_REFRESH));

        if (!refreshToken.isActive()) {
            throw reusoDeTokenRotado(refreshToken);
        }
        if (refreshToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("Refresh token expirado");
        }
        return refreshToken;
    }

    /**
     * Decide qué hacer con un refresh token que ya estaba rotado. Son dos situaciones muy
     * distintas con la misma pinta:
     *
     * <ul>
     *   <li><b>Reintento del cliente.</b> La rotación salió bien pero la respuesta se perdió en
     *       el camino, y el front mandó de nuevo el único token que tiene. En un local con mala
     *       conexión esto pasa, y cerrarle todas las sesiones sería castigar a la red.
     *   <li><b>Copia robada.</b> El token legítimo ya rotó hace rato y este reaparece. No hay
     *       forma de saber si quien renovó recién fue la persona o el ladrón, así que se cierran
     *       todas las sesiones y los dos vuelven a loguearse: el que sabe la contraseña entra.
     * </ul>
     *
     * <p>Los separa el reloj, que es el único dato disponible. Dentro de la ventana de gracia se
     * rechaza el pedido y nada más; pasada, se cierra todo.
     *
     * <p>El mensaje es el mismo en los dos casos y el mismo que el de un token inexistente: quien
     * tenga la copia no debería poder deducir que disparó la detección.
     */
    private RuntimeException reusoDeTokenRotado(RefreshToken refreshToken) {
        LocalDateTime revocadoEn = refreshToken.getRevokedAt();
        boolean reintentoReciente = revocadoEn != null
                && revocadoEn.isAfter(LocalDateTime.now().minusSeconds(GRACIA_REUSO_SEGUNDOS));

        if (reintentoReciente) {
            return new BadRequestException(RECHAZO_REFRESH);
        }

        // Directo al repositorio y no por revokeAllUserRefreshTokens: ese método es @Transactional
        // y llamarlo desde acá es una self-invocation, que no pasa por el proxy. Mejor no depender
        // de una anotación que en este camino no se aplica.
        int revocadas = refreshTokenRepository.revokeAllByUserId(refreshToken.getUserId(),
                LocalDateTime.now());
        log.warn("Reuso de un refresh token ya rotado del usuario {}: se cerraron {} sesiones",
                refreshToken.getUserId(), revocadas);

        // Tipo propio para que el rechazo no se lleve puesta la revocación de arriba: ver el
        // noRollbackFor de AuthService.refreshToken.
        return new RefreshTokenReusadoException(RECHAZO_REFRESH);
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