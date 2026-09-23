package com.SolucionesInformaticasBA.minimarket.modules.usuarios.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.SolucionesInformaticasBA.minimarket.modules.auth.api.AuthApi;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.api.dto.ActualizarUsuarioRequest;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.entity.Usuario;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.enums.EstadoUsuario;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.enums.Rol;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.repository.UsuarioRepository;
import com.SolucionesInformaticasBA.minimarket.shared.exeption.BadRequestException;
import com.SolucionesInformaticasBA.minimarket.shared.exeption.ForbiddenException;

/**
 * El PATCH de nombre y apellido, que era la única operación sobre otro usuario que no pasaba por
 * la jerarquía de roles y la única que dejaba llegar un valor vacío hasta el flush.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UsuarioServiceUpdateTest {

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

    // --- Jerarquía --------------------------------------------------------------------------

    @Test
    @DisplayName("un ADMIN no puede editar al SUPERADMIN: el hasRole del controller no alcanzaba")
    void adminNoEditaSuperadmin() {
        Usuario objetivo = registrar(usuario(Rol.SUPERADMIN));
        autenticar(usuario(Rol.ADMIN));

        assertThatThrownBy(() -> service.update(objetivo.getId(), request("Otro", null)))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("no puede editar");

        assertThat(objetivo.getNombre()).isEqualTo("Test");
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("un ADMIN tampoco puede editar a otro ADMIN")
    void adminNoEditaAdmin() {
        Usuario objetivo = registrar(usuario(Rol.ADMIN));
        autenticar(usuario(Rol.ADMIN));

        assertThatThrownBy(() -> service.update(objetivo.getId(), request("Otro", null)))
                .isInstanceOf(ForbiddenException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("un ADMIN sí edita a un EMPLEADO")
    void adminEditaEmpleado() {
        Usuario objetivo = registrar(usuario(Rol.EMPLEADO));
        autenticar(usuario(Rol.ADMIN));
        guardarDevolviendoLoRecibido();

        var response = service.update(objetivo.getId(), request("Nombre", "Apellido"));

        assertThat(response.getNombre()).isEqualTo("Nombre");
        assertThat(response.getApellido()).isEqualTo("Apellido");
    }

    @Test
    @DisplayName("editarse a uno mismo se permite, que es el único caso en que operar sobre la cuenta propia no es un error")
    void cadaUnoSeEditaASiMismo() {
        Usuario actor = registrar(usuario(Rol.EMPLEADO));
        autenticar(actor);
        guardarDevolviendoLoRecibido();

        var response = service.update(actor.getId(), request("Nuevo", null));

        assertThat(response.getNombre()).isEqualTo("Nuevo");
    }

    @Test
    @DisplayName("el permiso se valida antes que el dato: un ADMIN editando al SUPERADMIN con un nombre vacío da 403, no 400")
    void permisoAntesQueValidacion() {
        Usuario objetivo = registrar(usuario(Rol.SUPERADMIN));
        autenticar(usuario(Rol.ADMIN));

        assertThatThrownBy(() -> service.update(objetivo.getId(), request("   ", null)))
                .isInstanceOf(ForbiddenException.class);
    }

    // --- Valor vacío ------------------------------------------------------------------------

    @Test
    @DisplayName("el nombre en blanco es 400 y no el 500 que tiraba el @NotBlank de la entidad en el flush")
    void nombreEnBlancoEsBadRequest() {
        Usuario actor = registrar(usuario(Rol.EMPLEADO));
        autenticar(actor);

        assertThatThrownBy(() -> service.update(actor.getId(), request("   ", null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("nombre");

        assertThat(actor.getNombre()).isEqualTo("Test");
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("la cadena vacía se rechaza igual que los espacios")
    void nombreVacioEsBadRequest() {
        Usuario actor = registrar(usuario(Rol.EMPLEADO));
        autenticar(actor);

        assertThatThrownBy(() -> service.update(actor.getId(), request("", null)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("el apellido en blanco también, y nombra el campo que falla")
    void apellidoEnBlancoEsBadRequest() {
        Usuario actor = registrar(usuario(Rol.EMPLEADO));
        autenticar(actor);

        assertThatThrownBy(() -> service.update(actor.getId(), request(null, "  ")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("apellido");
    }

    @Test
    @DisplayName("el nulo no es un valor vacío: es el campo que no se toca")
    void elNuloDejaElValorAnterior() {
        Usuario actor = registrar(usuario(Rol.EMPLEADO));
        autenticar(actor);
        guardarDevolviendoLoRecibido();

        var response = service.update(actor.getId(), request(null, null));

        assertThat(response.getNombre()).isEqualTo("Test");
        assertThat(response.getApellido()).isEqualTo("Test");
    }

    @Test
    @DisplayName("los espacios de los costados se recortan: la colación es NO PAD y no los unifica")
    void seRecortanLosEspacios() {
        Usuario actor = registrar(usuario(Rol.EMPLEADO));
        autenticar(actor);
        guardarDevolviendoLoRecibido();

        var response = service.update(actor.getId(), request("  Juan  ", "  Pérez  "));

        assertThat(response.getNombre()).isEqualTo("Juan");
        assertThat(response.getApellido()).isEqualTo("Pérez");
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
                new UsernamePasswordAuthenticationToken(actor.getId().toString(), null, List.of()));
    }

    private void guardarDevolviendoLoRecibido() {
        when(userRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private ActualizarUsuarioRequest request(String nombre, String apellido) {
        var request = new ActualizarUsuarioRequest();
        request.setNombre(nombre);
        request.setApellido(apellido);
        return request;
    }
}
