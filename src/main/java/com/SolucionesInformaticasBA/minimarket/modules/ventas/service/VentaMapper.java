package com.SolucionesInformaticasBA.minimarket.modules.ventas.service;

import org.springframework.stereotype.Component;

import com.SolucionesInformaticasBA.minimarket.dto.response.VentaResponseDTO;
import com.SolucionesInformaticasBA.minimarket.model.entity.Venta;

@Component
public class VentaMapper {

    public VentaResponseDTO toDTO(Venta v) {
        VentaResponseDTO dto = new VentaResponseDTO();
        dto.setId(v.getId());
        dto.setFecha(v.getFecha());
        dto.setTotal(v.getTotal());
        return dto;
    }
}