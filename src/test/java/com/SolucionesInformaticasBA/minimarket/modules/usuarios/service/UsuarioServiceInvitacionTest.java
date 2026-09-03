package com.SolucionesInformaticasBA.minimarket.modules.usuarios.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
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
import com.SolucionesInformaticasBA.minimarket.shared.mail.EmailException;

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
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());
        when(userRepository.existsByUsername("ana")).thenReturn(true);
        when(userRepository.existsByUsername("ana2")).thenReturn(true);
        when(userRepository.existsByUsername("ana3")).thenReturn(false);
        guardaYDevuelve();

        var response = service.invitar(request("ana@ejemplo.com", null, null));

        assertThat(response.getUsername()).isEqualTo("ana3");
    }

    @Test
    @DisplayName("un username explícito ya tomado es 400, no se desambigua por su cuenta")
    void usernameExplicitoDuplicado() {
        autenticar(usuario(Rol.ADMIN));
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());
        when(userRepository.existsByUsername("anap")).thenReturn(true);

        assertThatThrownBy(() -> service.invitar(request("ana@ejemplo.com", "anap", null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("ya está en uso");

        verify(authApi, never()).enviarInvitacion(any(), anyString(), anyString());
    }

    @Test
    @DisplayName("el email duplicado corta antes de mandar nada")
    void emailDuplicado() {
        autenticar(usuario(Rol.ADMIN));
        when(userRepository.findByEmail("ana@ejemplo.com")).thenReturn(Optional.of(usuario(Rol.EMPLEADO)));

        assertThatThrownBy(() -> service.invitar(request("ana@ejemplo.com", null, null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("email ya está registrado");

        verify(authApi, never()).enviarInvitacion(any(), anyString(), anyString());
    }

    @Test
    @DisplayName("el email de una cuenta dada de baja se rechaza con su propio mensaje")
    void emailDeCuentaDadaDeBaja() {
        autenticar(usuario(Rol.ADMIN));
        Usuario baja = usuario(Rol.EMPLEADO);
        baja.setDeletedAt(LocalDateTime.now());
        when(userRepository.findByEmail("ana@ejemplo.com")).thenReturn(Optional.of(baja));

        // La unique key de la tabla no sabe de deleted_at: si esto no cortara acá, el alta
        // pasaría la validación y reventaría en el INSERT con un 409 genérico.
        assertThatThrownBy(() -> service.invitar(request("ana@ejemplo.com", null, null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("cuenta dada de baja");

        verify(userRepository, never()).saveAndFlush(any());
        verify(authApi, never()).enviarInvitacion(any(), anyString(), anyString());
    }

    @Test
    @DisplayName("el username de una cuenta dada de baja sigue ocupado")
    void usernameDeCuentaDadaDeBaja() {
        autenticar(usuario(Rol.ADMIN));
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());
        when(userRepository.existsByUsername("anap")).thenReturn(true);

        assertThatThrownBy(() -> service.invitar(request("ana@ejemplo.com", "anap", null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("ya está en uso");
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

    // --- Aceptación: establecerPasswordInicial -----------------------------------------------
    // La regla vive acá desde que auth dejó de tocar la entidad; auth solo delega.

    @Test
    @DisplayName("establecer la contraseña inicial activa la cuenta")
    void passwordInicialActivaLaCuenta() {
        Usuario invitado = registrar(pendiente());
        when(passwordEncoder.encode("MiPassword1!")).thenReturn("hash-nuevo");

        service.establecerPasswordInicial(invitado.getId(), "MiPassword1!", null);

        assertThat(invitado.getEstado()).isEqualTo(EstadoUsuario.ACTIVO);
        assertThat(invitado.getHashPassword()).isEqualTo("hash-nuevo");
        verify(userRepository).save(invitado);
    }

    @Test
    @DisplayName("una invitación de alguien bloqueado entre medio ya no vale")
    void invitacionDeBloqueadoNoVale() {
        Usuario invitado = pendiente();
        invitado.setEstado(EstadoUsuario.BLOQUEADO);
        registrar(invitado);

        assertThatThrownBy(() -> service.establecerPasswordInicial(invitado.getId(), "MiPassword1!", null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("ya no es válida");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("una invitación de alguien dado de baja tampoco, y no se delata como 404")
    void invitacionDeEliminadoNoVale() {
        Usuario invitado = pendiente();
        invitado.setDeletedAt(LocalDateTime.now());
        registrar(invitado);

        assertThatThrownBy(() -> service.establecerPasswordInicial(invitado.getId(), "MiPassword1!", null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("ya no es válida");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("una invitación de una cuenta que ya se activó por otro camino no vale")
    void invitacionDeCuentaYaActivaNoVale() {
        // Se activó reseteando la contraseña en vez de aceptar la invitación. El enlace que
        // le quedaba vivo no puede servir para cambiarle la contraseña sin conocer la actual.
        Usuario yaActivo = registrar(usuario(Rol.EMPLEADO));

        assertThatThrownBy(() -> service.establecerPasswordInicial(yaActivo.getId(), "MiPassword1!", null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("ya no es válida");

        verify(userRepository, never()).save(any());
    }

    // --- Restauración de una cuenta dada de baja ---------------------------------------------

    @Test
    @DisplayName("restaurar revive la fila original y le manda una invitación nueva")
    void restaurarRevivePendiente() {
        Usuario baja = usuario(Rol.EMPLEADO);
        baja.setDeletedAt(LocalDateTime.now());
        registrar(baja);
        autenticar(usuario(Rol.ADMIN));
        when(passwordEncoder.encode(anyString())).thenReturn("hash-inutilizable");
        when(userRepository.saveAndFlush(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = service.restaurar(baja.getId());

        assertThat(response.getEstado()).isEqualTo(EstadoUsuario.PENDIENTE);
        assertThat(response.getDeletedAt()).isNull();
        assertThat(response.getId()).isEqualTo(baja.getId());
        verify(authApi).enviarInvitacion(baja.getId(), baja.getEmail(), baja.getNombre());
    }

    @Test
    @DisplayName("la contraseña anterior no revive con la cuenta")
    void restaurarInvalidaLaPasswordVieja() {
        Usuario baja = usuario(Rol.EMPLEADO);
        baja.setDeletedAt(LocalDateTime.now());
        baja.setHashPassword("hash-viejo");
        registrar(baja);
        autenticar(usuario(Rol.ADMIN));
        when(passwordEncoder.encode(anyString())).thenReturn("hash-inutilizable");
        when(userRepository.saveAndFlush(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        service.restaurar(baja.getId());

        assertThat(baja.getHashPassword()).isEqualTo("hash-inutilizable");
    }

    @Test
    @DisplayName("no se restaura una cuenta que no está dada de baja")
    void restaurarCuentaEnPie() {
        Usuario enPie = registrar(usuario(Rol.EMPLEADO));
        autenticar(usuario(Rol.ADMIN));

        assertThatThrownBy(() -> service.restaurar(enPie.getId()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("no está dado de baja");

        verify(authApi, never()).enviarInvitacion(any(), anyString(), anyString());
    }

    @Test
    @DisplayName("si el mail de la restauración falla, la cuenta no revive")
    void restaurarConMailCaido() {
        Usuario baja = usuario(Rol.EMPLEADO);
        baja.setDeletedAt(LocalDateTime.now());
        registrar(baja);
        autenticar(usuario(Rol.ADMIN));
        when(passwordEncoder.encode(anyString())).thenReturn("hash-inutilizable");
        when(userRepository.saveAndFlush(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));
        doThrow(new EmailException("SMTP caído", null))
                .when(authApi).enviarInvitacion(any(), anyString(), anyString());

        // Propaga para que la transacción se vaya abajo: revivir una cuenta a la que nadie
        // puede entrar es peor que dejarla dada de baja.
        assertThatThrownBy(() -> service.restaurar(baja.getId()))
                .isInstanceOf(EmailException.class);
    }

    @Test
    @DisplayName("el email de una cuenta pendiente dice que hay que reenviar, no invitar de nuevo")
    void emailConInvitacionPendiente() {
        autenticar(usuario(Rol.ADMIN));
        when(userRepository.findByEmail("ana@ejemplo.com")).thenReturn(Optional.of(pendiente()));

        assertThatThrownBy(() -> service.invitar(request("ana@ejemplo.com", null, null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("reenviásela");
    }

    @Test
    @DisplayName("no se ofrece restaurar una cuenta sobre la que el actor no manda")
    void bajaDeOtroAdminNoOfreceRestaurar() {
        autenticar(usuario(Rol.ADMIN));
        Usuario bajaAdmin = usuario(Rol.ADMIN);
        bajaAdmin.setDeletedAt(LocalDateTime.now());
        when(userRepository.findByEmail("ana@ejemplo.com")).thenReturn(Optional.of(bajaAdmin));

        // El hecho se informa igual; la instrucción no, porque restaurar a un par da 403.
        assertThatThrownBy(() -> service.invitar(request("ana@ejemplo.com", null, null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("El email pertenece a una cuenta dada de baja");
    }

    @Test
    @DisplayName("tampoco se ofrece reenviarle la invitación a un par")
    void pendienteDeOtroAdminNoOfreceReenviar() {
        autenticar(usuario(Rol.ADMIN));
        Usuario pendienteAdmin = usuario(Rol.ADMIN);
        pendienteAdmin.setEstado(EstadoUsuario.PENDIENTE);
        when(userRepository.findByEmail("ana@ejemplo.com")).thenReturn(Optional.of(pendienteAdmin));

        assertThatThrownBy(() -> service.invitar(request("ana@ejemplo.com", null, null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Ese email ya tiene una invitación pendiente");
    }

    @Test
    @DisplayName("el SUPERADMIN sí ve la salida sobre la cuenta de un ADMIN")
    void superadminVeLaSalidaSobreUnAdmin() {
        autenticar(usuario(Rol.SUPERADMIN));
        Usuario bajaAdmin = usuario(Rol.ADMIN);
        bajaAdmin.setDeletedAt(LocalDateTime.now());
        when(userRepository.findByEmail("ana@ejemplo.com")).thenReturn(Optional.of(bajaAdmin));

        assertThatThrownBy(() -> service.invitar(request("ana@ejemplo.com", null, null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("restaurala");
    }

    @Test
    @DisplayName("el listado con bajas las trae con deletedAt, para poder distinguirlas")
    void listadoConBajas() {
        Usuario baja = usuario(Rol.EMPLEADO);
        baja.setDeletedAt(LocalDateTime.now());
        when(userRepository.findAll()).thenReturn(java.util.List.of(usuario(Rol.ADMIN), baja));

        var todos = service.getAll(true);

        assertThat(todos).hasSize(2);
        assertThat(todos).filteredOn(u -> u.getDeletedAt() != null).hasSize(1);
        verify(userRepository, never()).findAllByDeletedAtIsNull();
    }

    // --- Reseteo de contraseña como cierre alternativo del alta ------------------------------

    @Test
    @DisplayName("el reseteo activa una cuenta pendiente: el token viajó al mismo mail")
    void resetActivaCuentaPendiente() {
        Usuario invitado = registrar(pendiente());
        when(passwordEncoder.encode("MiPassword1!")).thenReturn("hash-nuevo");

        service.restablecerPassword(invitado.getId(), "MiPassword1!");

        // Sin esto quedaría con contraseña válida y sin poder entrar: el login exige ACTIVO.
        assertThat(invitado.getEstado()).isEqualTo(EstadoUsuario.ACTIVO);
        assertThat(invitado.getHashPassword()).isEqualTo("hash-nuevo");
    }

    @Test
    @DisplayName("el reseteo no destraba una cuenta bloqueada")
    void resetNoDestrabaBloqueado() {
        Usuario bloqueado = usuario(Rol.EMPLEADO);
        bloqueado.setEstado(EstadoUsuario.BLOQUEADO);
        registrar(bloqueado);
        when(passwordEncoder.encode("MiPassword1!")).thenReturn("hash-nuevo");

        service.restablecerPassword(bloqueado.getId(), "MiPassword1!");

        assertThat(bloqueado.getEstado()).isEqualTo(EstadoUsuario.BLOQUEADO);
        assertThat(bloqueado.getHashPassword()).isEqualTo("hash-nuevo");
    }

    @Test
    @DisplayName("el invitado puede elegir su propio nombre de usuario")
    void invitadoEligeSuUsername() {
        Usuario invitado = registrar(pendiente());
        when(userRepository.existsByUsername("ana.perez")).thenReturn(false);
        when(passwordEncoder.encode("MiPassword1!")).thenReturn("hash-nuevo");

        service.establecerPasswordInicial(invitado.getId(), "MiPassword1!", "ana.perez");

        assertThat(invitado.getUsername()).isEqualTo("ana.perez");
        assertThat(invitado.getEstado()).isEqualTo(EstadoUsuario.ACTIVO);
    }

    @Test
    @DisplayName("confirmar el username derivado no choca contra el propio registro")
    void confirmarElUsernameDerivado() {
        Usuario invitado = registrar(pendiente());
        String derivado = invitado.getUsername();
        // Su propia fila ya ocupa ese nombre: sin la comparación previa, esto sería un 400.
        when(userRepository.existsByUsername(derivado)).thenReturn(true);
        when(passwordEncoder.encode("MiPassword1!")).thenReturn("hash-nuevo");

        service.establecerPasswordInicial(invitado.getId(), "MiPassword1!", derivado);

        assertThat(invitado.getUsername()).isEqualTo(derivado);
        assertThat(invitado.getEstado()).isEqualTo(EstadoUsuario.ACTIVO);
    }

    @Test
    @DisplayName("un username tomado por otro es 400, y acá no se desambigua con sufijo")
    void usernameElegidoDuplicado() {
        Usuario invitado = registrar(pendiente());
        when(userRepository.existsByUsername("tomado")).thenReturn(true);

        assertThatThrownBy(() -> service.establecerPasswordInicial(invitado.getId(), "MiPassword1!", "tomado"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("ya está en uso");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("sin username elegido queda el que se derivó del email")
    void sinUsernameQuedaElDerivado() {
        Usuario invitado = registrar(pendiente());
        String derivado = invitado.getUsername();
        when(passwordEncoder.encode("MiPassword1!")).thenReturn("hash-nuevo");

        service.establecerPasswordInicial(invitado.getId(), "MiPassword1!", "   ");

        assertThat(invitado.getUsername()).isEqualTo(derivado);
    }

    @Test
    @DisplayName("getCuentaInvitada trae los datos si la invitación sigue en pie")
    void cuentaInvitadaVigente() {
        Usuario invitado = registrar(pendiente());

        var response = service.getCuentaInvitada(invitado.getId());

        assertThat(response.getUsername()).isEqualTo(invitado.getUsername());
        assertThat(response.getEmail()).isEqualTo(invitado.getEmail());
    }

    @Test
    @DisplayName("getCuentaInvitada aplica la misma regla de vigencia que la aceptación")
    void cuentaInvitadaBloqueada() {
        Usuario invitado = pendiente();
        invitado.setEstado(EstadoUsuario.BLOQUEADO);
        registrar(invitado);

        assertThatThrownBy(() -> service.getCuentaInvitada(invitado.getId()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("ya no es válida");
    }

    // --- Helpers ----------------------------------------------------------------------------

    private void sinDuplicados() {
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());
        when(userRepository.existsByUsername(anyString())).thenReturn(false);
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

    private Usuario pendiente() {
        Usuario u = usuario(Rol.EMPLEADO);
        u.setEstado(EstadoUsuario.PENDIENTE);
        u.setHashPassword("hash-inutilizable");
        return u;
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
