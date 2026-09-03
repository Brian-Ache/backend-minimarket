package com.SolucionesInformaticasBA.minimarket.modules.usuarios.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.SolucionesInformaticasBA.minimarket.modules.auth.api.AuthApi;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.api.dto.InvitarUsuarioRequest;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.entity.Usuario;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.enums.EstadoUsuario;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.enums.Rol;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.repository.UsuarioRepository;
import com.SolucionesInformaticasBA.minimarket.shared.exeption.BadRequestException;
import com.SolucionesInformaticasBA.minimarket.shared.exeption.ForbiddenException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UsuarioServiceInvitacionTest {

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

    @Test
    @DisplayName("el invitado queda PENDIENTE y se le manda el mail")
    void invitadoQuedaPendiente() {
        autenticar(usuario(Rol.ADMIN));
        sinDuplicados();
        guardaYDevuelve();

        var response = service.invitar(request("ana@ejemplo.com", null, null));

        assertThat(response.getEstado()).isEqualTo(EstadoUsuario.PENDIENTE);
        assertThat(response.getRol()).isEqualTo(Rol.EMPLEADO);
        verify(authApi).enviarInvitacion(any(), eq("ana@ejemplo.com"), eq("Ana"));
    }

    @Test
    @DisplayName("la cuenta nace con una contraseña aleatoria que nadie conoce")
    void passwordInutilizable() {
        autenticar(usuario(Rol.ADMIN));
        sinDuplicados();
        guardaYDevuelve();

        service.invitar(request("ana@ejemplo.com", null, null));

        var captor = ArgumentCaptor.forClass(String.class);
        verify(passwordEncoder).encode(captor.capture());
        assertThat(captor.getValue()).hasSizeGreaterThan(32);
    }

    @Test
    @DisplayName("sin username, se deriva de la parte local del email")
    void usernameDerivadoDelEmail() {
        autenticar(usuario(Rol.ADMIN));
        sinDuplicados();
        guardaYDevuelve();

        var response = service.invitar(request("Ana.Perez@ejemplo.com", null, null));

        assertThat(response.getUsername()).isEqualTo("ana.perez");
    }

    @Test
    @DisplayName("si el username derivado está tomado, se desambigua con un sufijo")
    void usernameDerivadoConColision() {
        autenticar(usuario(Rol.ADMIN));
        when(userRepository.existsByEmailAndDeletedAtIsNull(anyString())).thenReturn(false);
        when(userRepository.existsByUsernameAndDeletedAtIsNull("ana")).thenReturn(true);
        when(userRepository.existsByUsernameAndDeletedAtIsNull("ana2")).thenReturn(true);
        when(userRepository.existsByUsernameAndDeletedAtIsNull("ana3")).thenReturn(false);
        guardaYDevuelve();

        var response = service.invitar(request("ana@ejemplo.com", null, null));

        assertThat(response.getUsername()).isEqualTo("ana3");
    }

    @Test
    @DisplayName("un username explícito ya tomado es 400, no se desambigua por su cuenta")
    void usernameExplicitoDuplicado() {
        autenticar(usuario(Rol.ADMIN));
        when(userRepository.existsByEmailAndDeletedAtIsNull(anyString())).thenReturn(false);
        when(userRepository.existsByUsernameAndDeletedAtIsNull("anap")).thenReturn(true);

        assertThatThrownBy(() -> service.invitar(request("ana@ejemplo.com", "anap", null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("ya está en uso");

        verify(authApi, never()).enviarInvitacion(any(), anyString(), anyString());
    }

    @Test
    @DisplayName("el email duplicado corta antes de mandar nada")
    void emailDuplicado() {
        autenticar(usuario(Rol.ADMIN));
        when(userRepository.existsByEmailAndDeletedAtIsNull("ana@ejemplo.com")).thenReturn(true);

        assertThatThrownBy(() -> service.invitar(request("ana@ejemplo.com", null, null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("email ya está registrado");

        verify(authApi, never()).enviarInvitacion(any(), anyString(), anyString());
    }

    @Test
    @DisplayName("un ADMIN no puede invitar a otro ADMIN")
    void adminNoInvitaAdmin() {
        autenticar(usuario(Rol.ADMIN));

        assertThatThrownBy(() -> service.invitar(request("otro@ejemplo.com", null, Rol.ADMIN)))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("no puede dar de alta");

        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("el SUPERADMIN sí puede invitar a un ADMIN")
    void superadminInvitaAdmin() {
        autenticar(usuario(Rol.SUPERADMIN));
        sinDuplicados();
        guardaYDevuelve();

        var response = service.invitar(request("otro@ejemplo.com", null, Rol.ADMIN));

        assertThat(response.getRol()).isEqualTo(Rol.ADMIN);
    }

    // --- Reenvío ----------------------------------------------------------------------------

    @Test
    @DisplayName("se reenvía la invitación de una cuenta pendiente")
    void reenvioDePendiente() {
        Usuario objetivo = registrar(usuario(Rol.EMPLEADO));
        objetivo.setEstado(EstadoUsuario.PENDIENTE);
        autenticar(usuario(Rol.ADMIN));

        service.reenviarInvitacion(objetivo.getId());

        verify(authApi).enviarInvitacion(objetivo.getId(), objetivo.getEmail(), objetivo.getNombre());
    }

    @Test
    @DisplayName("no se reenvía la invitación de una cuenta que ya está activa")
    void noSeReenviaSiYaEstaActiva() {
        Usuario objetivo = registrar(usuario(Rol.EMPLEADO));
        autenticar(usuario(Rol.ADMIN));

        assertThatThrownBy(() -> service.reenviarInvitacion(objetivo.getId()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("cuenta pendiente");

        verify(authApi, never()).enviarInvitacion(any(), anyString(), anyString());
    }

    @Test
    @DisplayName("un ADMIN no puede reenviarle la invitación a otro ADMIN")
    void reenvioRespetaLaJerarquia() {
        Usuario objetivo = registrar(usuario(Rol.ADMIN));
        objetivo.setEstado(EstadoUsuario.PENDIENTE);
        autenticar(usuario(Rol.ADMIN));

        assertThatThrownBy(() -> service.reenviarInvitacion(objetivo.getId()))
                .isInstanceOf(ForbiddenException.class);

        verify(authApi, never()).enviarInvitacion(any(), anyString(), anyString());
    }

    // --- Helpers ----------------------------------------------------------------------------

    private void sinDuplicados() {
        when(userRepository.existsByEmailAndDeletedAtIsNull(anyString())).thenReturn(false);
        when(userRepository.existsByUsernameAndDeletedAtIsNull(anyString())).thenReturn(false);
    }

    private void guardaYDevuelve() {
        when(userRepository.saveAndFlush(any(Usuario.class))).thenAnswer(inv -> {
            Usuario u = inv.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });
    }

    private InvitarUsuarioRequest request(String email, String username, Rol rol) {
        var request = new InvitarUsuarioRequest();
        request.setNombre("Ana");
        request.setApellido("Pérez");
        request.setEmail(email);
        request.setUsername(username);
        request.setRol(rol);
        return request;
    }

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

    private Usuario registrar(Usuario u) {
        when(userRepository.findById(u.getId())).thenReturn(Optional.of(u));
        return u;
    }

    private void autenticar(Usuario actor) {
        registrar(actor);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(actor.getId().toString(), null, java.util.List.of()));
    }
}
