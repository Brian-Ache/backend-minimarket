package com.SolucionesInformaticasBA.minimarket.modules.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.SolucionesInformaticasBA.minimarket.modules.auth.entity.RefreshToken;
import com.SolucionesInformaticasBA.minimarket.modules.auth.repository.AuthTokensRepository;
import com.SolucionesInformaticasBA.minimarket.modules.auth.repository.RefreshTokenRepository;
import com.SolucionesInformaticasBA.minimarket.shared.exeption.BadRequestException;

/**
 * Detección de reuso del refresh token.
 *
 * <p>La rotación sola no alcanzaba: la consulta pedía solo los tokens activos, así que uno robado
 * y ya rotado daba vacío igual que uno inventado. Al ladrón le bastaba con no insistir.
 *
 * <p>Lo delicado no es detectar el reuso sino no confundirlo con un reintento del cliente, que en
 * un local con mala conexión pasa de verdad. Por eso casi todos los casos de acá son sobre la
 * ventana de gracia.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TokenServiceReusoTest {

    @Mock
    private AuthTokensRepository authTokensRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @InjectMocks
    private TokenService service;

    // --- Token robado -----------------------------------------------------------------------

    @Test
    @DisplayName("un token ya rotado hace rato cierra todas las sesiones del usuario")
    void tokenRotadoViejoCierraTodo() {
        RefreshToken token = disponible(rotadoHace(5, java.time.temporal.ChronoUnit.MINUTES));

        assertThatThrownBy(() -> service.validateRefreshToken("crudo"))
                .isInstanceOf(BadRequestException.class);

        verify(refreshTokenRepository).revokeAllByUserId(eq(token.getUserId()), any());
    }

    @Test
    @DisplayName("el rechazo usa un tipo propio, para que la revocación no se vaya abajo con la transacción")
    void elRechazoNoPuedeRevertirLaRevocacion() {
        disponible(rotadoHace(5, java.time.temporal.ChronoUnit.MINUTES));

        // AuthService.refreshToken declara este tipo en noRollbackFor. Si dejara de ser este
        // tipo, las sesiones cerradas por sospecha de robo volverían a abrirse solas.
        assertThatThrownBy(() -> service.validateRefreshToken("crudo"))
                .isInstanceOf(RefreshTokenReusadoException.class);
    }

    @Test
    @DisplayName("el mensaje no delata que se disparó la detección: es el mismo de un token inventado")
    void elMensajeNoDelataLaDeteccion() {
        disponible(rotadoHace(5, java.time.temporal.ChronoUnit.MINUTES));
        String mensajeReuso = mensajeDe(() -> service.validateRefreshToken("crudo"));

        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());
        String mensajeInexistente = mensajeDe(() -> service.validateRefreshToken("crudo"));

        assertThat(mensajeReuso).isEqualTo(mensajeInexistente);
    }

    // --- Reintento del cliente ----------------------------------------------------------------

    @Test
    @DisplayName("un token rotado recién es un reintento del cliente: se rechaza, pero sin cerrarle las sesiones")
    void reintentoRecienteNoCierraNada() {
        disponible(rotadoHace(5, java.time.temporal.ChronoUnit.SECONDS));

        assertThatThrownBy(() -> service.validateRefreshToken("crudo"))
                .isInstanceOf(BadRequestException.class)
                .isNotInstanceOf(RefreshTokenReusadoException.class);

        verify(refreshTokenRepository, never()).revokeAllByUserId(any(), any());
    }

    @Test
    @DisplayName("pasada la gracia ya no se le concede el beneficio de la duda")
    void pasadaLaGraciaSeCierraTodo() {
        disponible(rotadoHace(31, java.time.temporal.ChronoUnit.SECONDS));

        assertThatThrownBy(() -> service.validateRefreshToken("crudo"))
                .isInstanceOf(RefreshTokenReusadoException.class);

        verify(refreshTokenRepository).revokeAllByUserId(any(), any());
    }

    @Test
    @DisplayName("un token inactivo sin fecha de revocación se trata como robado, no como reintento")
    void sinFechaDeRevocacionSeAsumeLoPeor() {
        RefreshToken token = tokenBase();
        token.setActive(false);
        token.setRevokedAt(null);
        disponible(token);

        assertThatThrownBy(() -> service.validateRefreshToken("crudo"))
                .isInstanceOf(RefreshTokenReusadoException.class);

        verify(refreshTokenRepository).revokeAllByUserId(any(), any());
    }

    // --- Lo que no cambió ---------------------------------------------------------------------

    @Test
    @DisplayName("un token que no existe sigue siendo un rechazo simple, sin cerrar sesiones de nadie")
    void tokenInexistenteNoCierraNada() {
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.validateRefreshToken("crudo"))
                .isInstanceOf(BadRequestException.class)
                .isNotInstanceOf(RefreshTokenReusadoException.class);

        verify(refreshTokenRepository, never()).revokeAllByUserId(any(), any());
    }

    @Test
    @DisplayName("un token activo pero vencido se rechaza por vencido, no por reuso")
    void tokenVencidoNoEsReuso() {
        RefreshToken token = tokenBase();
        token.setExpiresAt(LocalDateTime.now().minusHours(1));
        disponible(token);

        assertThatThrownBy(() -> service.validateRefreshToken("crudo"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("expirado");

        verify(refreshTokenRepository, never()).revokeAllByUserId(any(), any());
    }

    @Test
    @DisplayName("el token en pie se valida y se devuelve, que es el camino de todos los días")
    void tokenValidoPasa() {
        RefreshToken token = disponible(tokenBase());

        assertThat(service.validateRefreshToken("crudo")).isSameAs(token);

        verify(refreshTokenRepository, never()).revokeAllByUserId(any(), any());
    }

    // --- Helpers ------------------------------------------------------------------------------

    private RefreshToken tokenBase() {
        return RefreshToken.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .tokenHash("hash")
                .expiresAt(LocalDateTime.now().plusHours(720))
                .isActive(true)
                .build();
    }

    private RefreshToken rotadoHace(long cantidad, java.time.temporal.ChronoUnit unidad) {
        RefreshToken token = tokenBase();
        token.setActive(false);
        token.setRevokedAt(LocalDateTime.now().minus(cantidad, unidad));
        return token;
    }

    /** Deja al token disponible para la consulta por hash, como si estuviera en la base. */
    private RefreshToken disponible(RefreshToken token) {
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));
        return token;
    }

    private String mensajeDe(Runnable accion) {
        try {
            accion.run();
            throw new AssertionError("Se esperaba un rechazo");
        } catch (BadRequestException e) {
            return e.getMessage();
        }
    }
}
