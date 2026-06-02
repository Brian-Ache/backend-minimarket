package com.SolucionesInformaticasBA.minimarket.mapper;

import java.util.List;

import org.springframework.stereotype.Component;

import com.SolucionesInformaticasBA.minimarket.dto.response.CompraResponseDTO;
import com.SolucionesInformaticasBA.minimarket.dto.response.DetalleCompraResponseDTO;
import com.SolucionesInformaticasBA.minimarket.model.entity.Compra;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class CompraMapper {

    private final DetalleCompraMapper detalleCompraMapper;

    public CompraResponseDTO toDTO(Compra compra) {

        CompraResponseDTO dto = new CompraResponseDTO();

        dto.setId(compra.getIdCompra());
        dto.setFecha(compra.getFecha());
        dto.setTotal(compra.getTotal());

        List<DetalleCompraResponseDTO> detalles = compra.getDetalles()
                .stream()
                .map(detalleCompraMapper::toDTO)
                .toList();

        dto.setDetalles(detalles);

        return dto;
    }
}
