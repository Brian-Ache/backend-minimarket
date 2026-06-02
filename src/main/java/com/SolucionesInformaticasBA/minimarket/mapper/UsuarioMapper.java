package com.SolucionesInformaticasBA.minimarket.mapper;

import org.springframework.stereotype.Component;

import com.SolucionesInformaticasBA.minimarket.dto.request.UsuarioRequestDTO;
import com.SolucionesInformaticasBA.minimarket.dto.response.UsuarioResponseDTO;
import com.SolucionesInformaticasBA.minimarket.model.entity.Usuario;
import com.SolucionesInformaticasBA.minimarket.model.enums.Rol;

@Component
public class UsuarioMapper {

    public Usuario toEntity(UsuarioRequestDTO dto) {
        Usuario u = new Usuario();
        u.setUsername(dto.getUsername());
        u.setPassword(dto.getPassword()); // ⚠️ después encriptar
        u.setRol(Rol.valueOf(dto.getRol()));
        return u;
    }

    public UsuarioResponseDTO toDTO(Usuario u) {
        UsuarioResponseDTO dto = new UsuarioResponseDTO();
        dto.setId(u.getId());
        dto.setUsername(u.getUsername());
        dto.setRol(u.getRol().name());
        return dto;
    }
}