package com.SolucionesInformaticasBA.minimarket.modules.usuario.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.SolucionesInformaticasBA.minimarket.modules.usuario.entity.Usuario;


public interface UsuarioRepository extends JpaRepository<Usuario, UUID>{
    // FechaEliminacionIsNull para filtrar directamente los soft deletes
    Usuario findByIdAndFechaEliminacionIsNull(UUID id);
    Usuario findByUsernameAndFechaEliminacionIsNull(String username);
    List<Usuario> findAllByFechaEliminacionIsNull();
}
