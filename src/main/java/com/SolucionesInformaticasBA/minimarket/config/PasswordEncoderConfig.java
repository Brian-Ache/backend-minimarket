package com.SolucionesInformaticasBA.minimarket.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * El encoder vive fuera de SecurityConfig a propósito: SecurityConfig depende del
 * JwtAuthenticationFilter, que a su vez necesita consultar el usuario, cuyo servicio depende
 * del encoder. Teniéndolo acá se evita la dependencia circular.
 */
@Configuration
public class PasswordEncoderConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
