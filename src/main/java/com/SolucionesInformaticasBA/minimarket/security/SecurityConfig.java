package com.SolucionesInformaticasBA.minimarket.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import org.springframework.http.HttpStatus;

import lombok.RequiredArgsConstructor;

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    /**
     * Matriz de permisos. Criterio: el EMPLEADO opera el día a día (vender, cobrar, comprar,
     * mover stock y caja) pero no administra el negocio (usuarios, catálogo, reportes, cortes).
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> {})
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        // Sin token válido: 401 (el front debe reloguear).
                        // Con token pero sin permisos: 403, vía GlobalExceptionHandler.
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/auth/v1/**",
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/actuator/health",
                                // El contenedor hace un forward a /error para renderizar el
                                // cuerpo del 403. Sin permitirlo, ese forward se deniega como
                                // anónimo y el 401 termina pisando al 403 original.
                                "/error")
                        .permitAll()

                        // --- Solo ADMIN ---
                        // Usuarios: /me y change-password se resuelven con @PreAuthorize
                        // en el controller, porque dependen de quién es el dueño del recurso.
                        .requestMatchers("/api/reportes/**").hasRole("ADMIN")
                        .requestMatchers("/api/caja/v1/corte", "/api/caja/v1/corte/**").hasRole("ADMIN")

                        // Catálogo: lectura para todos, escritura solo ADMIN
                        .requestMatchers(HttpMethod.POST, "/api/productos/**", "/api/categorias/**",
                                "/api/proveedores/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/productos/**", "/api/categorias/**",
                                "/api/proveedores/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/productos/**", "/api/categorias/**",
                                "/api/proveedores/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/productos/**", "/api/categorias/**",
                                "/api/proveedores/**").hasRole("ADMIN")

                        // Anular ventas y compras es una operación sensible
                        .requestMatchers(HttpMethod.DELETE, "/api/ventas/**", "/api/compras/**").hasRole("ADMIN")

                        // --- Resto: cualquier usuario autenticado (ADMIN o EMPLEADO) ---
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
