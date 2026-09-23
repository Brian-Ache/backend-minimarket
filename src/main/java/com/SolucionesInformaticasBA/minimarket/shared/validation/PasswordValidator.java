package com.SolucionesInformaticasBA.minimarket.shared.validation;

import java.nio.charset.StandardCharsets;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class PasswordValidator implements ConstraintValidator<Password, String> {

    private int minCaracteres;
    private int maxBytes;

    @Override
    public void initialize(Password constraint) {
        this.minCaracteres = constraint.minCaracteres();
        this.maxBytes = constraint.maxBytes();
    }

    /**
     * El nulo y el vacío se dan por válidos acá: los rechaza el {@code @NotBlank} que acompaña a
     * la anotación. Si también se quejara este validador, un campo ausente devolvería dos
     * errores por el mismo motivo.
     */
    @Override
    public boolean isValid(String password, ConstraintValidatorContext context) {
        if (password == null || password.isEmpty()) {
            return true;
        }

        if (password.length() < minCaracteres) {
            return rechazar(context,
                    "La contraseña debe tener al menos " + minCaracteres + " caracteres");
        }

        // En bytes y no en caracteres, que es como los cuenta BCrypt.
        if (password.getBytes(StandardCharsets.UTF_8).length > maxBytes) {
            return rechazar(context, "La contraseña no puede superar los " + maxBytes
                    + " bytes; los acentos y los emoji ocupan más de uno");
        }

        return true;
    }

    private boolean rechazar(ConstraintValidatorContext context, String mensaje) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(mensaje).addConstraintViolation();
        return false;
    }
}
