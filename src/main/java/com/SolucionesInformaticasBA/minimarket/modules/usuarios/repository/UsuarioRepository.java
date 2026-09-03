package com.SolucionesInformaticasBA.minimarket.modules.usuarios.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.SolucionesInformaticasBA.minimarket.modules.usuarios.entity.Usuario;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.enums.EstadoUsuario;



public interface UsuarioRepository extends JpaRepository<Usuario, UUID> {

    Optional<Usuario> findByEmailAndDeletedAtIsNull(String email);

    Optional<Usuario> findByUsernameAndDeletedAtIsNull(String username);

    Optional<Usuario> findByEmailAndDeletedAtIsNullAndEstado(String email, EstadoUsuario estado);

    Optional<Usuario> findByUsernameAndDeletedAtIsNullAndEstado(String username, EstadoUsuario estado);

    boolean existsByEmailAndDeletedAtIsNull(String email);

    // Los dos que siguen ignoran a propósito el borrado lógico: las unique keys uk_usuarios_email
    // y uk_usuarios_username no incluyen deleted_at, así que una cuenta dada de baja sigue
    // ocupando su email y su username. El alta los usa para rechazar el duplicado con un mensaje
    // claro, en vez de chocar contra la restricción en el INSERT.
    Optional<Usuario> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByIdAndDeletedAtIsNull(UUID id);

    // Usado por el filtro JWT: un usuario dado de baja o deshabilitado no puede seguir
    // operando aunque su token todavía no haya expirado. Trae la fila entera y no un boolean
    // porque el filtro necesita además el rol vigente, que puede haber cambiado después de
    // emitido el token.
    Optional<Usuario> findByIdAndDeletedAtIsNullAndEstado(UUID id, EstadoUsuario estado);

    List<Usuario> findAllByDeletedAtIsNull();
}
