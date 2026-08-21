package com.SolucionesInformaticasBA.minimarket.modules.usuarios.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.SolucionesInformaticasBA.minimarket.modules.auth.api.AuthApi;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.api.dto.CambiarRolRequest;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.api.dto.CrearUsuarioRequest;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.entity.Usuario;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.enums.EstadoUsuario;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.enums.Rol;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.repository.UsuarioRepository;
import com.SolucionesInformaticasBA.minimarket.shared.exeption.BadRequestException;
import com.SolucionesInformaticasBA.minimarket.shared.exeption.ForbiddenException;

/**
 * Permisos que dependen de la jerarquía de roles y que los {@code @PreAuthorize} del controller
 * no pueden decidir, porque ahí todavía no se conoce el rol del objetivo.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UsuarioServiceJerarquiaTest {

    @Mock
    private UsuarioRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthApi authApi;

    @InjectMocks
    private UsuarioService service;

    @AfterEach
    void limpiarContexto() {
        SecurityContextHolder.clearContext();
    }

    // --- Alta -------------------------------------------------------------------------------

    @Test
    @DisplayName("el SUPERADMIN puede dar de alta un ADMIN")
    void superadminCreaAdmin() {
        autenticar(usuario(Rol.SUPERADMIN));
        when(userRepository.existsByEmailAndDeletedAtIsNull(anyString())).thenReturn(false);
        when(userRepository.existsByUsernameAndDeletedAtIsNull(anyString())).thenReturn(false);
        when(userRepository.saveAndFlush(any(Usuario.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        var response = service.crear(crearRequest(Rol.ADMIN));

        assertThat(response.getRol()).isEqualTo(Rol.ADMIN);
        assertThat(response.getEstado()).isEqualTo(EstadoUsuario.ACTIVO);
    }

    @Test
    @DisplayName("el ADMIN no puede dar de alta otro ADMIN")
    void adminNoCreaAdmin() {
        autenticar(usuario(Rol.ADMIN));

        assertThatThrownBy(() -> service.crear(crearRequest(Rol.ADMIN)))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("no puede dar de alta");

        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("ni siquiera el SUPERADMIN puede crear otro SUPERADMIN: la llave viene del seed")
    void nadieCreaSuperadmin() {
        autenticar(usuario(Rol.SUPERADMIN));

        assertThatThrownBy(() -> service.crear(crearRequest(Rol.SUPERADMIN)))
                .isInstanceOf(ForbiddenException.class);

        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("el rol se valida antes que el email duplicado: primero el permiso, después el dato")
    void permisoAntesQueValidacion() {
        autenticar(usuario(Rol.ADMIN));

        assertThatThrownBy(() -> service.crear(crearRequest(Rol.ADMIN)))
                .isInstanceOf(ForbiddenException.class);

        verify(userRepository, never()).existsByEmailAndDeletedAtIsNull(anyString());
    }

    // --- Cambio de rol ----------------------------------------------------------------------

    @Test
    @DisplayName("el SUPERADMIN promueve un EMPLEADO a ADMIN y le corta las sesiones")
    void superadminPromueveEmpleado() {
        Usuario objetivo = registrar(usuario(Rol.EMPLEADO));
        autenticar(usuario(Rol.SUPERADMIN));
        when(userRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = service.cambiarRol(objetivo.getId(), cambiarRolRequest(Rol.ADMIN));

        assertThat(response.getRol()).isEqualTo(Rol.ADMIN);
        verify(authApi).revokeAllSessions(objetivo.getId());
    }

    @Test
    @DisplayName("el SUPERADMIN degrada un ADMIN a EMPLEADO")
    void superadminDegradaAdmin() {
        Usuario objetivo = registrar(usuario(Rol.ADMIN));
        autenticar(usuario(Rol.SUPERADMIN));
        when(userRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = service.cambiarRol(objetivo.getId(), cambiarRolRequest(Rol.EMPLEADO));

        assertThat(response.getRol()).isEqualTo(Rol.EMPLEADO);
    }

    @Test
    @DisplayName("un ADMIN no puede fabricarse un par promoviendo a un EMPLEADO")
    void adminNoPromueveAAdmin() {
        Usuario objetivo = registrar(usuario(Rol.EMPLEADO));
        autenticar(usuario(Rol.ADMIN));

        assertThatThrownBy(() -> service.cambiarRol(objetivo.getId(), cambiarRolRequest(Rol.ADMIN)))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("no puede asignar el rol ADMIN");

        assertThat(objetivo.getRol()).isEqualTo(Rol.EMPLEADO);
        verify(authApi, never()).revokeAllSessions(any());
    }

    @Test
    @DisplayName("un ADMIN no puede degradar a otro ADMIN")
    void adminNoDegradaAdmin() {
        Usuario objetivo = registrar(usuario(Rol.ADMIN));
        autenticar(usuario(Rol.ADMIN));

        assertThatThrownBy(
                () -> service.cambiarRol(objetivo.getId(), cambiarRolRequest(Rol.EMPLEADO)))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("no puede cambiarle el rol");

        assertThat(objetivo.getRol()).isEqualTo(Rol.ADMIN);
    }

    @Test
    @DisplayName("nadie asigna el rol SUPERADMIN, ni el propio SUPERADMIN")
    void nadieAsignaSuperadmin() {
        Usuario objetivo = registrar(usuario(Rol.ADMIN));
        autenticar(usuario(Rol.SUPERADMIN));

        assertThatThrownBy(
                () -> service.cambiarRol(objetivo.getId(), cambiarRolRequest(Rol.SUPERADMIN)))
                .isInstanceOf(ForbiddenException.class);

        assertThat(objetivo.getRol()).isEqualTo(Rol.ADMIN);
    }

    @Test
    @DisplayName("nadie se cambia el rol a sí mismo")
    void noSeCambiaElRolASiMismo() {
        Usuario actor = registrar(usuario(Rol.SUPERADMIN));
        autenticar(actor);

        assertThatThrownBy(() -> service.cambiarRol(actor.getId(), cambiarRolRequest(Rol.ADMIN)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("a vos mismo");
    }

    @Test
    @DisplayName("asignar el rol que el usuario ya tiene es 400, no un cambio vacío")
    void mismoRolEsBadRequest() {
        Usuario objetivo = registrar(usuario(Rol.EMPLEADO));
        autenticar(usuario(Rol.ADMIN));

        assertThatThrownBy(
                () -> service.cambiarRol(objetivo.getId(), cambiarRolRequest(Rol.EMPLEADO)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("ya tiene el rol");

        verify(authApi, never()).revokeAllSessions(any());
    }

    // --- Bloqueo y baja ---------------------------------------------------------------------

    @Test
    @DisplayName("un ADMIN no puede bloquear a otro ADMIN")
    void adminNoBloqueaAdmin() {
        Usuario objetivo = registrar(usuario(Rol.ADMIN));
        autenticar(usuario(Rol.ADMIN));

        assertThatThrownBy(() -> service.bloquear(objetivo.getId()))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("no puede bloquear");

        assertThat(objetivo.getEstado()).isEqualTo(EstadoUsuario.ACTIVO);
        verify(authApi, never()).revokeAllSessions(any());
    }

    @Test
    @DisplayName("un ADMIN no puede bloquear al SUPERADMIN")
    void adminNoBloqueaSuperadmin() {
        Usuario objetivo = registrar(usuario(Rol.SUPERADMIN));
        autenticar(usuario(Rol.ADMIN));

        assertThatThrownBy(() -> service.bloquear(objetivo.getId()))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("el SUPERADMIN bloquea a un ADMIN y le corta las sesiones")
    void superadminBloqueaAdmin() {
        Usuario objetivo = registrar(usuario(Rol.ADMIN));
        autenticar(usuario(Rol.SUPERADMIN));
        when(userRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = service.bloquear(objetivo.getId());

        assertThat(response.getEstado()).isEqualTo(EstadoUsuario.BLOQUEADO);
        verify(authApi).revokeAllSessions(objetivo.getId());
    }

    @Test
    @DisplayName("nadie se bloquea a sí mismo, ni el SUPERADMIN")
    void noSeBloqueaASiMismo() {
        Usuario actor = registrar(usuario(Rol.SUPERADMIN));
        autenticar(actor);

        assertThatThrownBy(() -> service.bloquear(actor.getId()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("a vos mismo");
    }

    @Test
    @DisplayName("un ADMIN no puede eliminar al SUPERADMIN")
    void adminNoEliminaSuperadmin() {
        Usuario objetivo = registrar(usuario(Rol.SUPERADMIN));
        autenticar(usuario(Rol.ADMIN));

        assertThatThrownBy(() -> service.delete(objetivo.getId()))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("no puede eliminar");

        assertThat(objetivo.getDeletedAt()).isNull();
        verify(authApi, never()).revokeAllSessions(any());
    }

    @Test
    @DisplayName("un ADMIN sí elimina a un EMPLEADO")
    void adminEliminaEmpleado() {
        Usuario objetivo = registrar(usuario(Rol.EMPLEADO));
        autenticar(usuario(Rol.ADMIN));

        service.delete(objetivo.getId());

        assertThat(objetivo.getDeletedAt()).isNotNull();
        verify(authApi).revokeAllSessions(objetivo.getId());
    }

    @Test
    @DisplayName("un ADMIN no puede desbloquear a otro ADMIN")
    void adminNoDesbloqueaAdmin() {
        Usuario objetivo = registrar(usuario(Rol.ADMIN));
        objetivo.setEstado(EstadoUsuario.BLOQUEADO);
        autenticar(usuario(Rol.ADMIN));

        assertThatThrownBy(() -> service.desbloquear(objetivo.getId()))
                .isInstanceOf(ForbiddenException.class);

        assertThat(objetivo.getEstado()).isEqualTo(EstadoUsuario.BLOQUEADO);
    }

    // --- Helpers ----------------------------------------------------------------------------

    private Usuario usuario(Rol rol) {
        return Usuario.builder()
                .id(UUID.randomUUID())
                .nombre("Test")
                .apellido("Test")
                .username("user-" + UUID.randomUUID())
                .email(UUID.randomUUID() + "@test.local")
                .hashPassword("hash")
                .rol(rol)
                .estado(EstadoUsuario.ACTIVO)
                .build();
    }

    /** Deja al usuario disponible para {@code findById}, como si estuviera en la base. */
    private Usuario registrar(Usuario u) {
        when(userRepository.findById(u.getId())).thenReturn(Optional.of(u));
        return u;
    }

    /** Pone al usuario como el autenticado de la request y lo deja visible en la base. */
    private void autenticar(Usuario actor) {
        registrar(actor);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(actor.getId().toString(), null, java.util.List.of()));
    }

    private CambiarRolRequest cambiarRolRequest(Rol rol) {
        var request = new CambiarRolRequest();
        request.setRol(rol);
        return request;
    }

    private CrearUsuarioRequest crearRequest(Rol rol) {
        var request = new CrearUsuarioRequest();
        request.setNombre("Nuevo");
        request.setApellido("Usuario");
        request.setEmail("nuevo@test.local");
        request.setUsername("nuevo");
        request.setPassword("Password1!");
        request.setRol(rol);
        return request;
    }
}
