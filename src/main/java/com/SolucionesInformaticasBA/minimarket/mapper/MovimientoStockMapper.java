package com.SolucionesInformaticasBA.minimarket.mapper;

import org.springframework.stereotype.Component;

import com.SolucionesInformaticasBA.minimarket.dto.request.MovimientoStockRequestDTO;
import com.SolucionesInformaticasBA.minimarket.dto.response.MovimientoStockResponseDTO;
import com.SolucionesInformaticasBA.minimarket.model.entity.MovimientoStock;
import com.SolucionesInformaticasBA.minimarket.model.entity.Producto;
import com.SolucionesInformaticasBA.minimarket.model.entity.Usuario;
import com.SolucionesInformaticasBA.minimarket.model.enums.TipoMovimiento;

@Component
public class MovimientoStockMapper {

    public MovimientoStock toEntity(MovimientoStockRequestDTO dto, Producto producto, Usuario usuario) {
        MovimientoStock m = new MovimientoStock();
        m.setProducto(producto);
        m.setCantidad(dto.getCantidad());
        m.setTipo(TipoMovimiento.valueOf(dto.getTipo()));
        m.setMotivo(dto.getMotivo());
        m.setUsuario(usuario);
        return m;
    }

    public MovimientoStockResponseDTO toDTO(MovimientoStock m) {
        MovimientoStockResponseDTO dto = new MovimientoStockResponseDTO();
        dto.setId(m.getId());
        dto.setProductoId(m.getProducto().getId());
        dto.setCantidad(m.getCantidad());
        dto.setTipo(m.getTipo().name());
        dto.setMotivo(m.getMotivo());
        dto.setFecha(m.getFecha());
        return dto;
    }
}
