package com.SolucionesInformaticasBA.minimarket.modules.usuarios.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.SolucionesInformaticasBA.minimarket.modules.usuarios.entity.Usuario;



public interface UsuarioRepository extends JpaRepository<Usuario, UUID> {

    Optional<Usuario> findByEmailAndDeletedAtIsNull(String email);

    Optional<Usuario> findByEmailAndDeletedAtIsNullAndEnabledTrue(String email);

    boolean existsByEmailAndDeletedAtIsNull(String email);

    Optional<Usuario> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByIdAndDeletedAtIsNull(UUID id);

    // Usado por el filtro JWT: un usuario dado de baja o deshabilitado no puede seguir
    // operando aunque su token todavía no haya expirado.
    boolean existsByIdAndDeletedAtIsNullAndEnabledTrue(UUID id);

    List<Usuario> findAllByDeletedAtIsNull();
}
