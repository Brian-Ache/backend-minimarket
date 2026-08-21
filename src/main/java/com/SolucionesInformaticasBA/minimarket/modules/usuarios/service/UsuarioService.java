package com.SolucionesInformaticasBA.minimarket.modules.usuarios.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.SolucionesInformaticasBA.minimarket.modules.auth.api.AuthApi;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.api.UsuarioApi;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.api.dto.*;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.entity.Usuario;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.enums.EstadoUsuario;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.enums.Rol;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.repository.UsuarioRepository;
import com.SolucionesInformaticasBA.minimarket.shared.SecurityUtils;
import com.SolucionesInformaticasBA.minimarket.shared.exeption.BadRequestException;
import com.SolucionesInformaticasBA.minimarket.shared.exeption.ForbiddenException;
import com.SolucionesInformaticasBA.minimarket.shared.exeption.ResourceNotFoundException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UsuarioService implements UsuarioApi {

    private final UsuarioRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthApi authApi;

    /**
     * Alta de usuarios. Cada quien da de alta por debajo de su nivel: el SUPERADMIN crea ADMIN
     * y EMPLEADO, el ADMIN solo EMPLEADO. El usuario queda habilitado de entrada porque lo crea
     * alguien de confianza con su contraseña: no hay autorregistro ni verificación por email
     * en el MVP.
     */
    @Override
    @Transactional
    public UsuarioResponse crear(CrearUsuarioRequest request) {
        Rol rolNuevo = request.getRol() != null ? request.getRol() : Rol.EMPLEADO;
        Usuario actor = usuarioAutenticado();

        // Nadie manda sobre su propio nivel, así que esto también deja fuera la creación de
        // otro SUPERADMIN: la llave maestra viene del seed, no de un endpoint.
        if (!actor.getRol().mandaSobre(rolNuevo)) {
            throw new ForbiddenException(
                    "Un " + actor.getRol() + " no puede dar de alta a un " + rolNuevo);
        }

        if (userRepository.existsByEmailAndDeletedAtIsNull(request.getEmail())) {
            throw new BadRequestException("El email ya está registrado");
        }
        if (userRepository.existsByUsernameAndDeletedAtIsNull(request.getUsername())) {
            throw new BadRequestException("El nombre de usuario ya está en uso");
        }

        Usuario u = Usuario.builder()
                .nombre(request.getNombre())
                .apellido(request.getApellido())
                .email(request.getEmail())
                .username(request.getUsername())
                .hashPassword(passwordEncoder.encode(request.getPassword()))
                .rol(rolNuevo)
                // Lo da de alta alguien de mayor jerarquía, con su contraseña, así que ya
                // puede operar: no hay circuito de verificación por email en el MVP.
                .estado(EstadoUsuario.ACTIVO)
                .build();

        // saveAndFlush: sin el flush, created_at/updated_at todavía no están en la entidad
        // y la respuesta del alta saldría con esos campos en null.
        return toUserResponse(userRepository.saveAndFlush(u));
    }

    @Override
    public Usuario getUsuarioById(UUID id){
        return findActiveUser(id);
    }

    @Override
    public UsuarioResponse getById(UUID id) {
        Usuario u = findActiveUser(id);
        return toUserResponse(u);
    }

    @Override
    public UsuarioResponse getByEmail(String email) {
        Usuario u = userRepository.findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
        return toUserResponse(u);
    }

    @Override
    public List<UsuarioResponse> getAll() {
        return userRepository.findAllByDeletedAtIsNull().stream()
                .map(this::toUserResponse)
                .toList();
    }

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

        u = userRepository.save(u);
        return toUserResponse(u);
    }

    /**
     * Promueve o degrada a un usuario. Solo entre roles por debajo del propio: el SUPERADMIN
     * mueve entre ADMIN y EMPLEADO, el ADMIN no puede fabricar otro ADMIN ni tocar a uno.
     *
     * <p>Corta las sesiones del usuario: el rol viaja en el JWT y el front decide qué mostrar
     * con ese dato, así que tiene que volver a loguearse para recibir un token que diga la
     * verdad. Sus permisos reales, eso sí, cambian en la request siguiente, porque el filtro
     * lee el rol de la base y no del token.
     */
    @Override
    @Transactional
    public UsuarioResponse cambiarRol(UUID id, CambiarRolRequest request) {
        Usuario u = findActiveUser(id);
        Usuario actor = exigirMandoSobre(u, "cambiarle el rol", "cambiarte el rol");

        Rol rolNuevo = request.getRol();

        // El rol nuevo también tiene que estar por debajo del actor: si no, un ADMIN se
        // fabricaría un par —o un SUPERADMIN— y se saltearía la jerarquía por la ventana.
        if (!actor.getRol().mandaSobre(rolNuevo)) {
            throw new ForbiddenException(
                    "Un " + actor.getRol() + " no puede asignar el rol " + rolNuevo);
        }
        if (u.getRol() == rolNuevo) {
            throw new BadRequestException("El usuario ya tiene el rol " + rolNuevo);
        }

        u.setRol(rolNuevo);
        u = userRepository.save(u);
        authApi.revokeAllSessions(id);

        return toUserResponse(u);
    }

    /**
     * Suspende el acceso sin borrar la cuenta: el usuario conserva su historial y puede
     * reactivarse. Cortar las sesiones es parte del bloqueo, si no seguiría operando con el
     * token que ya tenía en la mano.
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
        u = userRepository.save(u);
        authApi.revokeAllSessions(id);

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
        userRepository.save(u);

        // Sin esto el usuario dado de baja seguiría operando con sus tokens vigentes.
        authApi.revokeAllSessions(id);
    }

    @Override
    @Transactional
    public void changePassword(UUID id, CambiarPasswordRequest request) {
        Usuario u = findActiveUser(id);

        if (!passwordEncoder.matches(request.getPassActual(), u.getHashPassword())) {
            throw new BadRequestException("La contraseña actual no es correcta");
        }

        u.setHashPassword(passwordEncoder.encode(request.getNuevoPass()));
        userRepository.save(u);
    }

    public boolean existById(UUID id){
        return userRepository.existsByIdAndDeletedAtIsNull(id);
    }

    @Override
    public Optional<Rol> rolVigente(UUID id) {
        return userRepository.findByIdAndDeletedAtIsNullAndEstado(id, EstadoUsuario.ACTIVO)
                .map(Usuario::getRol);
    }

    /**
     * Exige que quien ejecuta la operación esté por encima del objetivo en la jerarquía, y
     * devuelve al actor para no volver a buscarlo.
     *
     * <p>Los {@code @PreAuthorize} del controller solo miran el rol de quien llama; la decisión
     * completa necesita también el rol del objetivo, y eso solo se sabe acá. Sin esta regla, un
     * ADMIN podría bloquear o borrar al SUPERADMIN, o dos ADMIN podrían sacarse del sistema
     * entre sí.
     *
     * <p>El rol del actor se relee de la base y no se toma del JWT: si acaban de degradarlo, su
     * token todavía dice ADMIN y seguiría mandando hasta que expire.
     *
     * @param accion       infinitivo que encaja en "Un ADMIN no puede {accion} a un ADMIN"
     * @param accionPropia infinitivo que encaja en "No podés {accionPropia} a vos mismo"
     */
    private Usuario exigirMandoSobre(Usuario objetivo, String accion, String accionPropia) {
        Usuario actor = usuarioAutenticado();

        if (actor.getId().equals(objetivo.getId())) {
            throw new BadRequestException("No podés " + accionPropia + " a vos mismo");
        }
        if (!actor.getRol().mandaSobre(objetivo.getRol())) {
            throw new ForbiddenException(
                    "Un " + actor.getRol() + " no puede " + accion + " a un " + objetivo.getRol());
        }
        return actor;
    }

    /** El usuario detrás del JWT de la request en curso, tal como está hoy en la base. */
    private Usuario usuarioAutenticado() {
        return findActiveUser(SecurityUtils.getCurrentUserId());
    }

    private Usuario findActiveUser(UUID id) {
        Usuario u = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        if (u.getDeletedAt() != null) {
            throw new ResourceNotFoundException("Usuario no encontrado");
        }

        return u;
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
                .build();
    }
}
