package com.SolucionesInformaticasBA.minimarket.modules.usuarios.repository;

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
}
