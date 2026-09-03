package com.SolucionesInformaticasBA.minimarket.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.SolucionesInformaticasBA.minimarket.modules.usuarios.api.UsuarioApi;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.enums.Rol;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;

/**
 * Lo que importa acá es de dónde sale el rol: de la base en cada request, no del claim del JWT.
 * Si saliera del token, degradar a alguien no tendría efecto hasta que su token expirara.
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtProvider jwtProvider;

    @Mock
    private UsuarioApi usuarioApi;

    @InjectMocks
    private JwtAuthenticationFilter filter;

    private final UUID userId = UUID.randomUUID();

    @AfterEach
    void limpiarContexto() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("un SUPERADMIN recibe también las authorities de ADMIN y EMPLEADO")
    void superadminEjerceLosRolesDeAbajo() throws Exception {
        tokenValido();
        when(usuarioApi.rolVigente(userId)).thenReturn(Optional.of(Rol.SUPERADMIN));

        ejecutar();

        assertThat(authorities())
                .containsExactlyInAnyOrder("ROLE_SUPERADMIN", "ROLE_ADMIN", "ROLE_EMPLEADO");
    }

    @Test
    @DisplayName("un EMPLEADO no recibe la authority de ADMIN")
    void empleadoNoEjerceAdmin() throws Exception {
        tokenValido();
        when(usuarioApi.rolVigente(userId)).thenReturn(Optional.of(Rol.EMPLEADO));

        ejecutar();

        assertThat(authorities()).containsExactly("ROLE_EMPLEADO");
    }

    @Test
    @DisplayName("degradado a EMPLEADO, su token viejo de ADMIN ya no le da permisos de ADMIN")
    void elRolSaleDeLaBaseYNoDelToken() throws Exception {
        // El token se emitió cuando todavía era ADMIN; la base ya dice EMPLEADO.
        tokenValido();
        when(usuarioApi.rolVigente(userId)).thenReturn(Optional.of(Rol.EMPLEADO));

        ejecutar();

        assertThat(authorities()).doesNotContain("ROLE_ADMIN");
    }

    @Test
    @DisplayName("un usuario bloqueado o dado de baja queda sin autenticación")
    void sinRolVigenteNoAutentica() throws Exception {
        tokenValido();
        when(usuarioApi.rolVigente(userId)).thenReturn(Optional.empty());

        var chain = ejecutar();

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        // La request sigue: quien decide el 401 es la cadena de Spring Security, no el filtro.
        verify(chain).doFilter(any(), any());
    }

    @Test
    @DisplayName("sin header Authorization no se consulta la base")
    void sinTokenNoConsulta() throws Exception {
        var request = new MockHttpServletRequest();
        filter.doFilter(request, new MockHttpServletResponse(), mock(FilterChain.class));

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(usuarioApi, never()).rolVigente(any());
    }

    // --- Helpers ----------------------------------------------------------------------------

    private void tokenValido() {
        var claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn(userId.toString());
        when(jwtProvider.validateToken(anyString())).thenReturn(claims);
    }

    private FilterChain ejecutar() throws Exception {
        var request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer un-token-cualquiera");
        var chain = mock(FilterChain.class);

        filter.doFilter(request, new MockHttpServletResponse(), chain);
        return chain;
    }

    private List<String> authorities() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
    }
}
