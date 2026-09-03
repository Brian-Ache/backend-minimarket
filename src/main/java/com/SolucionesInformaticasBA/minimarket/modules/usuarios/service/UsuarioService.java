package com.SolucionesInformaticasBA.minimarket.modules.usuarios.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.annotation.Lazy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.SolucionesInformaticasBA.minimarket.modules.auth.api.AuthApi;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.api.UsuarioApi;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.api.dto.ActualizarUsuarioRequest;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.api.dto.CambiarPasswordRequest;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.api.dto.CambiarRolRequest;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.api.dto.InvitarUsuarioRequest;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.api.dto.UsuarioResponse;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.entity.Usuario;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.enums.EstadoUsuario;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.enums.Rol;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.repository.UsuarioRepository;
import com.SolucionesInformaticasBA.minimarket.shared.SecurityUtils;
import com.SolucionesInformaticasBA.minimarket.shared.exeption.BadRequestException;
import com.SolucionesInformaticasBA.minimarket.shared.exeption.ForbiddenException;
import com.SolucionesInformaticasBA.minimarket.shared.exeption.ResourceNotFoundException;

import lombok.RequiredArgsConstructor;

/**
 * Cuentas del sistema: altas, estado y jerarquía de roles.
 *
 * <p>De auth solo conoce {@link AuthApi}; la entidad {@code Usuario} y su repositorio no salen
 * de este paquete: los demás módulos entran por {@link UsuarioApi}.
 *
 * <p>Solo lectura por defecto: cada método que escribe lleva su propio {@code @Transactional}.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UsuarioService implements UsuarioApi {

    /** Rol del alta que no pide ninguno: el de menos privilegio. */
    private static final Rol ROL_POR_DEFECTO = Rol.EMPLEADO;

    /** Tope del username derivado del email, para no pasarse de la columna. */
    private static final int LARGO_MAXIMO_USERNAME = 40;

    private final UsuarioRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    
    @Lazy
    private final AuthApi authApi;

    // Alta

    /**
     * Única alta del sistema: la cuenta nace PENDIENTE y la persona define su contraseña desde
     * el mail. No hay alta directa —quien invita nunca conoce la credencial del invitado— ni
     * autorregistro: el SUPERADMIN sale del seed y de ahí para abajo cada uno invita a los de
     * nivel menor.
     */
    @Override
    @Transactional
    public UsuarioResponse invitar(InvitarUsuarioRequest request) {
        Usuario actor = usuarioAutenticado();
        Rol rolNuevo = rolDeAlta(actor, request.getRol());

        exigirEmailLibre(request.getEmail(), actor.getRol());

        Usuario u = Usuario.builder()
                .nombre(request.getNombre())
                .apellido(request.getApellido())
                .email(request.getEmail())
                .username(resolverUsername(request))
                .hashPassword(passwordEncoder.encode(passwordInutilizable()))
                .rol(rolNuevo)
                .estado(EstadoUsuario.PENDIENTE)
                .build();

        // Sin el flush, created_at/updated_at saldrían en null en la respuesta.
        u = userRepository.saveAndFlush(u);

        // Si el mail falla, la transacción se va abajo con el usuario: no queda una cuenta
        // muerta ocupando el email y el username.
        authApi.enviarInvitacion(u.getId(), u.getEmail(), u.getNombre());

        return toUserResponse(u);
    }

    /**
     * Manda la invitación de nuevo con un token nuevo; el anterior queda invalidado. Para el
     * caso normal: el enlace venció o el mail no llegó.
     */
    @Override
    @Transactional
    public void reenviarInvitacion(UUID id) {
        Usuario u = findActiveUser(id);
        exigirMandoSobre(u, "reenviarle la invitación", "reenviarte la invitación");

        if (u.getEstado() != EstadoUsuario.PENDIENTE) {
            throw new BadRequestException(
                    "Solo se puede reenviar la invitación de una cuenta pendiente");
        }

        authApi.enviarInvitacion(u.getId(), u.getEmail(), u.getNombre());
    }

    // Consultas

    @Override
    public UsuarioResponse getById(UUID id) {
        return toUserResponse(findActiveUser(id));
    }

    @Override
    public UsuarioResponse getByEmail(String email) {
        return userRepository.findByEmailAndDeletedAtIsNull(email)
                .map(this::toUserResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
    }

    @Override
    public List<UsuarioResponse> getAll(boolean incluirBajas) {
        List<Usuario> usuarios = incluirBajas
                ? userRepository.findAll()
                : userRepository.findAllByDeletedAtIsNull();

        return usuarios.stream()
                .map(this::toUserResponse)
                .toList();
    }

    @Override
    public boolean existById(UUID id) {
        return userRepository.existsByIdAndDeletedAtIsNull(id);
    }

    @Override
    public boolean existsByEmail(String email) {
        return userRepository.existsByEmailAndDeletedAtIsNull(email);
    }

    /**
     * Rol con el que opera hoy. Lo lee el filtro JWT en cada request, así ve los cambios de rol
     * y las bajas sin esperar a que expire el token.
     */
    @Override
    public Optional<Rol> rolVigente(UUID id) {
        return userRepository.findByIdAndDeletedAtIsNullAndEstado(id, EstadoUsuario.ACTIVO)
                .map(Usuario::getRol);
    }

    // Datos, estado y rol

    @Override
    @Transactional
    public UsuarioResponse update(UUID id, ActualizarUsuarioRequest request) {
        Usuario u = findActiveUser(id);

        if (request.getNombre() != null) {
            u.setNombre(request.getNombre());
        }
        if (request.getApellido() != null) {
            u.setApellido(request.getApellido());
        }

        return toUserResponse(userRepository.save(u));
    }

    /**
     * Promueve o degrada, solo entre roles por debajo del propio.
     *
     * <p>Corta las sesiones porque el rol viaja en el JWT y el front decide con ese dato. Los
     * permisos reales ya cambian en la request siguiente: el filtro lee el rol de la base.
     */
    @Override
    @Transactional
    public UsuarioResponse cambiarRol(UUID id, CambiarRolRequest request) {
        Usuario u = findActiveUser(id);
        Usuario actor = exigirMandoSobre(u, "cambiarle el rol", "cambiarte el rol");

        Rol rolNuevo = request.getRol();

        // El rol nuevo también va por debajo del actor: si no, un ADMIN se fabricaría un par.
        if (!actor.getRol().mandaSobre(rolNuevo)) {
            throw new ForbiddenException(
                    "Un " + actor.getRol() + " no puede asignar el rol " + rolNuevo);
        }
        if (u.getRol() == rolNuevo) {
            throw new BadRequestException("El usuario ya tiene el rol " + rolNuevo);
        }

        u.setRol(rolNuevo);
        guardarYCortarSesiones(u);

        return toUserResponse(u);
    }

    /**
     * Suspende el acceso sin borrar la cuenta. Cortar las sesiones es parte del bloqueo: si no,
     * seguiría operando con el token que ya tenía.
     */
    @Override
    @Transactional
    public UsuarioResponse bloquear(UUID id) {
        Usuario u = findActiveUser(id);
        exigirMandoSobre(u, "bloquear", "bloquearte");

        if (u.getEstado() == EstadoUsuario.BLOQUEADO) {
            throw new BadRequestException("El usuario ya está bloqueado");
        }

        u.setEstado(EstadoUsuario.BLOQUEADO);
        guardarYCortarSesiones(u);

        return toUserResponse(u);
    }

    /** Devuelve el acceso a una cuenta bloqueada. Tiene que volver a iniciar sesión. */
    @Override
    @Transactional
    public UsuarioResponse desbloquear(UUID id) {
        Usuario u = findActiveUser(id);
        exigirMandoSobre(u, "desbloquear", "desbloquearte");

        if (u.getEstado() != EstadoUsuario.BLOQUEADO) {
            throw new BadRequestException("El usuario no está bloqueado");
        }

        u.setEstado(EstadoUsuario.ACTIVO);
        return toUserResponse(userRepository.save(u));
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        Usuario u = findActiveUser(id);
        exigirMandoSobre(u, "eliminar", "eliminarte");

        u.setDeletedAt(LocalDateTime.now());

        // Si no, seguiría operando con sus tokens vigentes hasta que expiren.
        guardarYCortarSesiones(u);
    }

    /**
     * Revive una cuenta dada de baja con una invitación nueva, como si se la diera de alta otra
     * vez pero sobre su propia fila: así conserva su historial de ventas, compras y movimientos,
     * que cuelgan de su id.
     *
     * <p>Vuelve PENDIENTE y con la contraseña anterior invalidada. Reactivar con la credencial
     * vieja resucitaría una contraseña que puede llevar meses sin uso, y sin ninguna señal de
     * que la persona siga controlando ese email; la invitación es esa señal.
     *
     * <p>No hace falta revisar el email ni el username: las unique keys no miran
     * {@code deleted_at}, así que siguieron reservados durante toda la baja.
     */
    @Override
    @Transactional
    public UsuarioResponse restaurar(UUID id) {
        Usuario u = buscarPorId(id);

        if (u.getDeletedAt() == null) {
            throw new BadRequestException("El usuario no está dado de baja");
        }
        exigirMandoSobre(u, "restaurar", "restaurarte");

        u.setDeletedAt(null);
        u.setEstado(EstadoUsuario.PENDIENTE);
        u.setHashPassword(passwordEncoder.encode(passwordInutilizable()));
        u = userRepository.saveAndFlush(u);

        // Igual que en el alta: si el mail no sale, la restauración se va abajo con él y la
        // cuenta queda dada de baja como estaba, en vez de revivir sin forma de entrar.
        authApi.enviarInvitacion(u.getId(), u.getEmail(), u.getNombre());

        return toUserResponse(u);
    }

    // Contraseñas

    @Override
    @Transactional
    public void changePassword(UUID id, CambiarPasswordRequest request) {
        Usuario u = findActiveUser(id);

        if (!passwordEncoder.matches(request.getPassActual(), u.getHashPassword())) {
            throw new BadRequestException("La contraseña actual no es correcta");
        }

        cambiarPassword(u, request.getNuevoPass());
    }

    /**
     * Además de la contraseña, habilita la cuenta si todavía estaba pendiente: el token del
     * reseteo viajó al email de la cuenta, que es la misma prueba de identidad que pide la
     * invitación. Sin esto, quien resetea en lugar de aceptar la invitación se queda con una
     * contraseña válida y sin poder entrar nunca, porque el login exige una cuenta activa.
     *
     * <p>Una cuenta bloqueada no se destraba por acá: ahí el impedimento no es la credencial.
     */
    @Override
    @Transactional
    public void restablecerPassword(UUID id, String password) {
        Usuario u = findActiveUser(id);

        if (u.getEstado() == EstadoUsuario.PENDIENTE) {
            u.setEstado(EstadoUsuario.ACTIVO);
        }

        cambiarPassword(u, password);
    }

    /**
     * Email primero y username después, en dos consultas y no un OR: si alguien tuviera como
     * username el email de otro, un OR devolvería dos filas y reventaría. La segunda consulta
     * solo sale si la primera vino vacía.
     *
     * <p>El estado va en la consulta: una cuenta pendiente o bloqueada ni llega a que se le
     * compare la contraseña.
     */
    @Override
    public Optional<UsuarioResponse> verificarCredenciales(String identificador, String password) {
        String id = normalizar(identificador);

        return userRepository.findByEmailAndDeletedAtIsNullAndEstado(id, EstadoUsuario.ACTIVO)
                .or(() -> userRepository.findByUsernameAndDeletedAtIsNullAndEstado(id, EstadoUsuario.ACTIVO))
                .filter(u -> passwordEncoder.matches(password, u.getHashPassword()))
                .map(this::toUserResponse);
    }

    /** Misma precedencia email→username que {@link #verificarCredenciales}, sin filtrar estado. */
    @Override
    public Optional<UsuarioResponse> buscarPorIdentificador(String identificador) {
        String id = normalizar(identificador);

        return userRepository.findByEmailAndDeletedAtIsNull(id)
                .or(() -> userRepository.findByUsernameAndDeletedAtIsNull(id))
                .map(this::toUserResponse);
    }

    // Aceptación de una invitación

    @Override
    public UsuarioResponse getCuentaInvitada(UUID id) {
        return toUserResponse(invitacionVigente(id));
    }

    @Override
    @Transactional
    public void establecerPasswordInicial(UUID id, String password, String username) {
        Usuario u = invitacionVigente(id);

        if (username != null && !username.isBlank()) {
            u.setUsername(usernameElegido(u, username.trim()));
        }

        u.setEstado(EstadoUsuario.ACTIVO);
        cambiarPassword(u, password);
    }

    // Reglas de jerarquía

    /**
     * Valida que el actor pueda repartir el rol pedido y lo devuelve; sin rol va
     * {@link #ROL_POR_DEFECTO}. Como nadie manda sobre su propio nivel, esto también impide
     * crear otro SUPERADMIN: esa llave viene del seed.
     */
    private Rol rolDeAlta(Usuario actor, Rol rolPedido) {
        Rol rolNuevo = rolPedido != null ? rolPedido : ROL_POR_DEFECTO;

        if (!actor.getRol().mandaSobre(rolNuevo)) {
            throw new ForbiddenException(
                    "Un " + actor.getRol() + " no puede dar de alta a un " + rolNuevo);
        }
        return rolNuevo;
    }

    /**
     * Exige que el actor esté por encima del objetivo en la jerarquía, y lo devuelve para no
     * volver a buscarlo. Los {@code @PreAuthorize} del controller solo ven el rol de quien
     * llama; sin esta regla un ADMIN podría borrar al SUPERADMIN o a otro ADMIN.
     *
     * <p>El caso propio se descarta contra el id del token, sin ir a la base. El rol del actor
     * sí se relee: si acaban de degradarlo, su token todavía dice ADMIN.
     *
     * @param accion       infinitivo para "Un ADMIN no puede {accion} a un ADMIN"
     * @param accionPropia infinitivo para "No podés {accionPropia} a vos mismo"
     */
    private Usuario exigirMandoSobre(Usuario objetivo, String accion, String accionPropia) {
        if (objetivo.getId().equals(SecurityUtils.getCurrentUserId())) {
            throw new BadRequestException("No podés " + accionPropia + " a vos mismo");
        }

        Usuario actor = usuarioAutenticado();
        if (!actor.getRol().mandaSobre(objetivo.getRol())) {
            throw new ForbiddenException(
                    "Un " + actor.getRol() + " no puede " + accion + " a un " + objetivo.getRol());
        }
        return actor;
    }

    /** El usuario del JWT en curso, tal como está hoy en la base. */
    private Usuario usuarioAutenticado() {
        return findActiveUser(SecurityUtils.getCurrentUserId());
    }

    // Unicidad de email y username

    /**
     * Mira también las cuentas dadas de baja. Las unique keys de la tabla no saben de
     * {@code deleted_at}: si acá se ignorara la baja lógica, el alta pasaría la validación y
     * reventaría recién en el INSERT, con un 409 genérico de integridad que no le dice al
     * administrador qué pasó.
     *
     * <p>Los mensajes distinguen los tres casos porque cada uno tiene una salida distinta, y
     * quien invita necesita saber cuál le toca: la cuenta pendiente se resuelve reenviándole la
     * invitación, la dada de baja restaurándola, y la que está en pie no se resuelve.
     */
    private void exigirEmailLibre(String email, Rol rolActor) {
        userRepository.findByEmail(email).ifPresent(u -> {
            throw new BadRequestException(mensajeEmailOcupado(u, rolActor));
        });
    }

    /**
     * Qué pasa con ese email y, si corresponde, qué hacer al respecto.
     *
     * <p>La salida se ofrece solo cuando el actor manda sobre la cuenta que lo ocupa: reenviar
     * y restaurar exigen esa misma jerarquía, así que proponerle a un ADMIN que restaure a otro
     * ADMIN sería mandarlo a un 403. El hecho se informa igual —explica por qué el email está
     * tomado y por qué la cuenta no aparece en el listado—; lo que se omite es la instrucción.
     */
    private String mensajeEmailOcupado(Usuario u, Rol rolActor) {
        boolean puedeGestionarla = rolActor.mandaSobre(u.getRol());

        if (u.getDeletedAt() != null) {
            return "El email pertenece a una cuenta dada de baja"
                    + (puedeGestionarla ? ": restaurala para volver a darle acceso" : "");
        }
        if (u.getEstado() == EstadoUsuario.PENDIENTE) {
            return "Ese email ya tiene una invitación pendiente"
                    + (puedeGestionarla ? ": reenviásela en lugar de invitarlo de nuevo" : "");
        }
        return "El email ya está registrado";
    }

    /** Incluye las cuentas dadas de baja, por lo mismo que {@link #exigirEmailLibre}. */
    private void exigirUsernameLibre(String username) {
        if (userRepository.existsByUsername(username)) {
            throw new BadRequestException("El nombre de usuario ya está en uso");
        }
    }

    /**
     * Username pedido, o derivado del email si no vino ninguno. Ante colisión agrega un sufijo
     * en vez de fallar: quien invita no sabe qué nombres están tomados.
     */
    private String resolverUsername(InvitarUsuarioRequest request) {
        if (request.getUsername() != null && !request.getUsername().isBlank()) {
            String pedido = request.getUsername().trim();
            exigirUsernameLibre(pedido);
            return pedido;
        }

        String base = request.getEmail().split("@")[0]
                .replaceAll("[^a-zA-Z0-9._-]", "")
                .toLowerCase();

        if (base.isBlank()) {
            base = "usuario";
        }
        base = base.substring(0, Math.min(base.length(), LARGO_MAXIMO_USERNAME));

        // Cuenta las bajas lógicas como ocupadas: su username sigue en la unique key.
        String candidato = base;
        int sufijo = 1;
        while (userRepository.existsByUsername(candidato)) {
            candidato = base + ++sufijo;
        }
        return candidato;
    }

    /**
     * Valida el username que eligió el invitado. Confirmar el que ya tiene es el caso normal,
     * por eso se compara antes de consultar: si no, chocaría contra su propio registro.
     *
     * <p>Acá la colisión no se resuelve con un sufijo como en {@link #resolverUsername}: lo
     * está eligiendo a mano y tiene que enterarse.
     */
    private String usernameElegido(Usuario u, String username) {
        if (!username.equals(u.getUsername())) {
            exigirUsernameLibre(username);
        }
        return username;
    }

    // Helpers

    /**
     * Contraseña que nadie conoce: se descarta apenas se hashea. La columna es NOT NULL y un
     * valor conocido sería una credencial válida esperando a que la prueben.
     */
    private String passwordInutilizable() {
        return UUID.randomUUID() + "-" + UUID.randomUUID();
    }

    private void cambiarPassword(Usuario u, String password) {
        u.setHashPassword(passwordEncoder.encode(password));
        userRepository.save(u);
    }

    /**
     * Guarda y cierra las sesiones abiertas. Van juntos en todo lo que cambia qué puede hacer
     * la cuenta —baja, bloqueo, cambio de rol—: el token viejo dejaría en pie el permiso que se
     * acaba de sacar.
     */
    private void guardarYCortarSesiones(Usuario u) {
        userRepository.save(u);
        authApi.revokeAllSessions(u.getId());
    }

    /**
     * La cuenta de una invitación que todavía sirve. No usa {@link #findActiveUser} porque acá
     * una cuenta borrada no es un 404 sino una invitación vencida: quien llega con el enlace no
     * tiene por qué saber si existió.
     *
     * <p>Exige que siga PENDIENTE, y no solo que no esté dada de baja ni bloqueada: la
     * invitación existe para cerrar un alta abierta. Una cuenta que ya se activó por otro
     * camino —{@link #restablecerPassword}— dejaría, si no, un enlace vivo capaz de cambiarle
     * la contraseña sin conocer la actual durante las horas que le queden de validez.
     */
    private Usuario invitacionVigente(UUID id) {
        Usuario u = buscarPorId(id);

        if (u.getDeletedAt() != null || u.getEstado() != EstadoUsuario.PENDIENTE) {
            // Lo dieron de baja, lo bloquearon o la cuenta ya se activó entre medio.
            throw new BadRequestException("La invitación ya no es válida");
        }

        return u;
    }

    private Usuario findActiveUser(UUID id) {
        Usuario u = buscarPorId(id);

        if (u.getDeletedAt() != null) {
            throw new ResourceNotFoundException("Usuario no encontrado");
        }

        return u;
    }

    private Usuario buscarPorId(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
    }

    private static String normalizar(String identificador) {
        return identificador == null ? "" : identificador.trim();
    }

    private UsuarioResponse toUserResponse(Usuario u) {
        return UsuarioResponse.builder()
                .id(u.getId())
                .nombre(u.getNombre())
                .apellido(u.getApellido())
                .username(u.getUsername())
                .email(u.getEmail())
                .rol(u.getRol())
                .estado(u.getEstado())
                .createdAt(u.getCreatedAt())
                .updatedAt(u.getUpdatedAt())
                .deletedAt(u.getDeletedAt())
                .build();
    }
}
