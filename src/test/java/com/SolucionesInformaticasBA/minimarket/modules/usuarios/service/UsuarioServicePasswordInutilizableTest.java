package com.SolucionesInformaticasBA.minimarket.modules.usuarios.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * La contraseña de relleno de una cuenta invitada tiene que poder pasar por BCrypt.
 *
 * <p>Este test existe por un 500 real: eran dos UUID con un guion en el medio, o sea 73 bytes,
 * y BCrypt no acepta más de 72. Spring Security 6 truncaba en silencio, así que funcionaba de
 * casualidad; la 7 tira {@code IllegalArgumentException} y se llevaba puesta la invitación
 * entera. El resto de los tests de invitación no lo veían porque mockean el encoder.
 */
class UsuarioServicePasswordInutilizableTest {

    /** El límite de BCrypt, que no es un detalle de implementación sino parte del algoritmo. */
    private static final int MAXIMO_BCRYPT = 72;

    @Test
    @DisplayName("la contraseña de relleno entra en el límite de BCrypt")
    void entraEnElLimiteDeBcrypt() {
        String password = UUID.randomUUID().toString();

        assertThat(password.getBytes(StandardCharsets.UTF_8).length)
            .as("BCrypt rechaza por encima de %d bytes", MAXIMO_BCRYPT)
            .isLessThanOrEqualTo(MAXIMO_BCRYPT);

        // Y que de verdad la acepte, que es lo único que importa.
        assertThat(new BCryptPasswordEncoder().encode(password)).isNotBlank();
    }

    @Test
    @DisplayName("dos UUID pegados no entran: es exactamente lo que rompía")
    void dosUuidNoEntran() {
        String comoEraAntes = UUID.randomUUID() + "-" + UUID.randomUUID();

        assertThat(comoEraAntes.getBytes(StandardCharsets.UTF_8).length)
            .isGreaterThan(MAXIMO_BCRYPT);
    }
}
