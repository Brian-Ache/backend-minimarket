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
                        // El listado de lo que la sincronización dejó marcado muestra el estado
                        // del inventario y de la caja de todo el local, no las ventas de quien
                        // pregunta: va con el resto de lo que solo ve un administrador. Tiene
                        // que declararse antes que la regla general de /api/ventas.
                        .requestMatchers(HttpMethod.GET, "/api/ventas/v1/revision").hasRole("ADMIN")
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

                        // Inventario: el ajuste manual y la baja de la fila de stock son las
                        // dos operaciones que pueden tapar un faltante sin dejar rastro
                        // operativo, así que van con el resto de lo sensible. Aumentar y
                        // disminuir siguen abiertos: son el movimiento normal del día.
                        .requestMatchers(HttpMethod.POST, "/api/inventario/v1/controlar").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/inventario/v1/lotes/ajustar").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/inventario/v1/stock/**").hasRole("ADMIN")

                        // Anular una compra sigue siendo solo de administrador: mueve plata a
                        // proveedores y no hay ninguna ventana que lo acote.
                        .requestMatchers(HttpMethod.DELETE, "/api/compras/**").hasRole("ADMIN")

                        // Anular una venta ya no. Un vendedor puede anular **las suyas** y solo
                        // dentro de la ventana de siete días, pero eso depende de quién es el
                        // dueño de la venta y de cuándo abrió su turno: son datos del objetivo,
                        // no de la ruta. La regla real vive en AnuladorVentas.validarPermiso,
                        // que es también por donde pasa la anulación que llega del front
                        // offline. Dejarla acá como hasRole("ADMIN") haría que el panel fuera
                        // más estricto que la sincronización, y anular sin conexión sería la
                        // forma de saltear el permiso.

                        // --- Resto: cualquier usuario autenticado (ADMIN o EMPLEADO) ---
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
