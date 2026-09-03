package com.SolucionesInformaticasBA.minimarket.security;


import java.io.IOException;
import java.util.List;
import java.util.UUID;

import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.SolucionesInformaticasBA.minimarket.modules.usuarios.api.UsuarioApi;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.enums.Rol;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtProvider jwtProvider;
    private final UsuarioApi usuarioApi;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        var token = extractToken(request);

        if (token != null) {
            try {
                var claims = jwtProvider.validateToken(token);
                var userId = claims.getSubject();

                // Un JWT válido no alcanza: el usuario pudo ser dado de baja, bloqueado o
                // cambiado de rol después de emitirlo, y el token seguiría vigente hasta
                // expirar. Por eso el rol sale de la base y no del claim: es el estado de
                // ahora, no el de cuando se logueó.
                var rolVigente = usuarioApi.rolVigente(UUID.fromString(userId));

                if (rolVigente.isEmpty()) {
                    SecurityContextHolder.clearContext();
                    filterChain.doFilter(request, response);
                    return;
                }

                var authorities = authoritiesDe(rolVigente.get());

                var authentication = new UsernamePasswordAuthenticationToken(
                        userId, null, authorities);

                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (Exception e) {
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Authorities del rol, con el prefijo {@code ROLE_} que esperan {@code hasRole(...)} y
     * {@code @PreAuthorize}.
     *
     * <p>Un SUPERADMIN recibe además las de ADMIN y EMPLEADO. La jerarquía se materializa acá,
     * en las authorities, en lugar de con un {@code RoleHierarchy} de Spring: vale igual para
     * las reglas de {@code SecurityConfig} y para los {@code @PreAuthorize}, sin que cada
     * {@code hasRole('ADMIN')} tenga que enumerar los roles de arriba.
     */
    private List<SimpleGrantedAuthority> authoritiesDe(Rol rol) {
        return rol.rolesQueEjerce().stream()
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r.name()))
                .toList();
    }

    private String extractToken(HttpServletRequest request) {
        var header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}
