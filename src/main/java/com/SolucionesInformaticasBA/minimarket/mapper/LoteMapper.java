package com.SolucionesInformaticasBA.minimarket.mapper;

import java.time.LocalDate;

import org.springframework.stereotype.Component;

import com.SolucionesInformaticasBA.minimarket.dto.request.LoteRequestDTO;
import com.SolucionesInformaticasBA.minimarket.dto.response.LoteResponseDTO;
import com.SolucionesInformaticasBA.minimarket.model.entity.Lote;
import com.SolucionesInformaticasBA.minimarket.model.entity.Producto;
import com.SolucionesInformaticasBA.minimarket.model.entity.Usuario;

@Component
public class LoteMapper {

    public Lote toEntity(LoteRequestDTO dto, Producto producto, Usuario usuario) {

        Lote lote = new Lote();

        lote.setProducto(producto);
        lote.setNumeroLote(dto.getNumeroLote());
        lote.setFechaVencimiento(dto.getFechaVencimiento());
        lote.setCantidad(dto.getCantidad());
        lote.setCreadoPor(usuario);

        return lote;
    }

    public LoteResponseDTO toDTO(Lote lote) {

        LoteResponseDTO dto = new LoteResponseDTO();

        dto.setId(lote.getId());
        dto.setProductoId(lote.getProducto().getId());
        dto.setNombreProducto(lote.getProducto().getNombre());
        dto.setNumeroLote(lote.getNumeroLote());
        dto.setFechaVencimiento(lote.getFechaVencimiento());
        dto.setCantidad(lote.getCantidad());
        dto.setEstado(this.calcularEstado(lote.getFechaVencimiento()));

        return dto;
    }

    public String calcularEstado(LocalDate fechaVencimiento) {

        if (fechaVencimiento == null) return "SIN_FECHA";

        LocalDate hoy = LocalDate.now();

        if (fechaVencimiento.isBefore(hoy)) {
            return "VENCIDO";
        }

        if (fechaVencimiento.isBefore(hoy.plusDays(7))) {
            return "PROXIMO";
        }

        return "VIGENTE";
    }
}