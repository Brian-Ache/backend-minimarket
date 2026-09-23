package com.SolucionesInformaticasBA.minimarket.shared.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Contraseña que BCrypt pueda hashear de verdad.
 *
 * <p>Reemplaza al {@code @Size(min = 8, max = 72)} que traían los DTOs de contraseña. Ese máximo
 * parecía cubrir el límite de BCrypt, pero {@code @Size} cuenta <b>caracteres</b> y BCrypt corta
 * en 72 <b>bytes</b>: una contraseña de 72 caracteres acentuados son 144 bytes, pasaba la
 * validación del request y reventaba recién en {@code passwordEncoder.encode()} con un
 * {@code IllegalArgumentException}. Como no hay handler para esa excepción, un error del cliente
 * terminaba saliendo como 500.
 *
 * <p>El mínimo sigue contándose en caracteres, que es lo que la persona escribe y lo que el
 * formulario le muestra.
 *
 * <p>Va siempre acompañada de {@code @NotBlank}: el valor ausente o vacío es asunto de aquella.
 */
@Documented
@Constraint(validatedBy = PasswordValidator.class)
@Target({ ElementType.FIELD, ElementType.PARAMETER })
@Retention(RetentionPolicy.RUNTIME)
public @interface Password {

    /** Sin uso: el validador arma un mensaje distinto para el mínimo y para el máximo. */
    String message() default "Contraseña inválida";

    /** Largo mínimo, en caracteres. */
    int minCaracteres() default 8;

    /** Tope de BCrypt, en bytes. No es un número elegible: es parte del algoritmo. */
    int maxBytes() default 72;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
