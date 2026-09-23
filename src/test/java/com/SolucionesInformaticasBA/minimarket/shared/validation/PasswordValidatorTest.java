package com.SolucionesInformaticasBA.minimarket.shared.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.SolucionesInformaticasBA.minimarket.modules.auth.api.dto.AceptarInvitacionRequest;
import com.SolucionesInformaticasBA.minimarket.modules.auth.api.dto.PasswordResetConfirmRequest;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.api.dto.CambiarPasswordRequest;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

/**
 * La contraseña que manda el usuario tiene que quedar rechazada en la validación del request, no
 * adentro de BCrypt.
 *
 * <p>Este test existe por un 500 real: los DTOs traían {@code @Size(min = 8, max = 72)} y ese 72
 * parecía el límite de BCrypt, pero {@code @Size} cuenta caracteres y BCrypt cuenta bytes. Una
 * contraseña de 72 caracteres acentuados son 144 bytes: pasaba la validación, llegaba a
 * {@code encode()} y salía {@code IllegalArgumentException}, que sin handler propio termina en el
 * catch-all del {@code GlobalExceptionHandler} como 500.
 *
 * <p>Es el mismo límite que ya documenta {@code UsuarioServicePasswordInutilizableTest}, que lo
 * cubre para la contraseña de relleno de una cuenta invitada. Acá se cubre para la que escribe la
 * persona, que es por donde entra un valor arbitrario.
 */
class PasswordValidatorTest {

    /** El límite de BCrypt, que no es un detalle de implementación sino parte del algoritmo. */
    private static final int MAXIMO_BCRYPT = 72;

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void abrirValidador() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void cerrarValidador() {
        factory.close();
    }

    // --- El caso que rompía ---------------------------------------------------------------

    @Test
    @DisplayName("72 caracteres acentuados son 144 bytes: BCrypt los rechaza, así que el request también")
    void acentosQueSePasanDeBytes() {
        String password = "á".repeat(MAXIMO_BCRYPT);

        assertThat(password.length()).isEqualTo(MAXIMO_BCRYPT);
        assertThat(password.getBytes(StandardCharsets.UTF_8).length).isGreaterThan(MAXIMO_BCRYPT);

        // Lo que pasaba después de que el request lo dejara entrar.
        assertThatThrownBy(() -> new BCryptPasswordEncoder().encode(password))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("72 bytes");

        assertThat(violaciones(password))
                .singleElement()
                .extracting(ConstraintViolation::getMessage)
                .asString()
                .contains("bytes");
    }

    @Test
    @DisplayName("un emoji ocupa cuatro bytes: 20 alcanzan para pasarse sin llegar a 72 caracteres")
    void emojiQueSePasaDeBytes() {
        String password = "🙂".repeat(20);

        assertThat(password.length()).isLessThan(MAXIMO_BCRYPT);
        assertThat(password.getBytes(StandardCharsets.UTF_8).length).isGreaterThan(MAXIMO_BCRYPT);
        assertThat(violaciones(password)).isNotEmpty();
    }

    // --- Lo que tiene que seguir entrando -------------------------------------------------

    @Test
    @DisplayName("72 caracteres ASCII son 72 bytes justos: eso no se toca")
    void asciiEnElLimiteEntra() {
        String password = "a".repeat(MAXIMO_BCRYPT);

        assertThat(password.getBytes(StandardCharsets.UTF_8).length).isEqualTo(MAXIMO_BCRYPT);
        assertThat(violaciones(password)).isEmpty();
        assertThat(new BCryptPasswordEncoder().encode(password)).isNotBlank();
    }

    @Test
    @DisplayName("una contraseña con acentos que entra en 72 bytes se acepta")
    void acentosQueEntranSeAceptan() {
        String password = "contraseñá-válida";

        assertThat(violaciones(password)).isEmpty();
        assertThat(new BCryptPasswordEncoder().encode(password)).isNotBlank();
    }

    // --- El mínimo, que se sigue contando en caracteres ------------------------------------

    @Test
    @DisplayName("siete caracteres siguen siendo pocos")
    void minimoEnCaracteres() {
        assertThat(violaciones("1234567"))
                .singleElement()
                .extracting(ConstraintViolation::getMessage)
                .asString()
                .contains("8 caracteres");
    }

    @Test
    @DisplayName("ocho caracteres alcanzan")
    void minimoJusto() {
        assertThat(violaciones("12345678")).isEmpty();
    }

    @Test
    @DisplayName("el vacío lo rechaza @NotBlank, no este validador: un solo error por campo")
    void elVacioEsAsuntoDeNotBlank() {
        var request = new PasswordResetConfirmRequest();
        request.setToken("token");
        request.setNewPassword("");

        assertThat(validator.validate(request))
                .singleElement()
                .extracting(ConstraintViolation::getMessage)
                .asString()
                .doesNotContain("bytes")
                .doesNotContain("caracteres");
    }

    // --- Los tres DTOs que hashean, para que ninguno quede afuera --------------------------

    @Test
    @DisplayName("aceptar una invitación rechaza la contraseña que BCrypt no puede hashear")
    void aceptarInvitacionValidaElLargo() {
        var request = new AceptarInvitacionRequest();
        request.setToken("token");
        request.setPassword("á".repeat(MAXIMO_BCRYPT));

        assertThat(validator.validate(request)).isNotEmpty();
    }

    @Test
    @DisplayName("cambiar la contraseña rechaza la nueva que BCrypt no puede hashear")
    void cambiarPasswordValidaLaNueva() {
        var request = new CambiarPasswordRequest();
        request.setPassActual("la-de-antes");
        request.setNuevoPass("á".repeat(MAXIMO_BCRYPT));

        assertThat(validator.validate(request)).isNotEmpty();
    }

    @Test
    @DisplayName("la contraseña actual no lleva el límite: contra ella solo se compara, no se hashea")
    void laActualNoSeValidaPorLargo() {
        var request = new CambiarPasswordRequest();
        request.setPassActual("á".repeat(MAXIMO_BCRYPT));
        request.setNuevoPass("una-contraseña-nueva");

        assertThat(validator.validate(request)).isEmpty();
    }

    // --- Helpers ---------------------------------------------------------------------------

    /** Valida la contraseña a través de un DTO real, no del validador suelto. */
    private Set<ConstraintViolation<PasswordResetConfirmRequest>> violaciones(String password) {
        var request = new PasswordResetConfirmRequest();
        request.setToken("token");
        request.setNewPassword(password);
        return validator.validate(request);
    }
}
