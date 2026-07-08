package com.SolucionesInformaticasBA.minimarket.repository;


import org.springframework.data.jpa.repository.JpaRepository;

import com.SolucionesInformaticasBA.minimarket.modules.usuarios.Entity.Usuario;

import java.util.Optional;
import java.util.UUID;

public interface UsuarioRepository extends JpaRepository<Usuario, UUID> {

    Optional<Usuario> findByUsername(String username);

	Usuario findByIdAndFechaEliminacionIsNull(UUID id);
}
