package com.SolucionesInformaticasBA.minimarket.mapper;

import org.springframework.stereotype.Component;

import com.SolucionesInformaticasBA.minimarket.dto.request.ProductoRequestDTO;
import com.SolucionesInformaticasBA.minimarket.dto.response.ProductoResponseDTO;
import com.SolucionesInformaticasBA.minimarket.model.entity.Producto;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.Entity.Usuario;


@Component
public class ProductoMapper {

    public Producto toEntity(ProductoRequestDTO dto, Usuario usuario) {
        Producto p = new Producto();
        p.setNombre(dto.getNombre());
        p.setBarcode(dto.getBarcode());
        p.setPrecio(dto.getPrecio());
        p.setManejaLotes(dto.getManejaLotes());
        p.setStock(dto.getStock());
        p.setIdUsuarioCreador(usuario.getId());
        return p;
    }

    public ProductoResponseDTO toDTO(Producto p) {
        ProductoResponseDTO dto = new ProductoResponseDTO();
        dto.setId(p.getId());
        dto.setNombre(p.getNombre());
        dto.setPrecio(p.getPrecio());
        dto.setBarcode(p.getBarcode());
        dto.setManejaLotes(p.isManejaLotes());
        dto.setStock(p.getStock());
        return dto;
    }
}
