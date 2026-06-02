package com.SolucionesInformaticasBA.minimarket.mapper;

import java.math.BigDecimal;

import org.springframework.stereotype.Component;

import com.SolucionesInformaticasBA.minimarket.model.entity.DetalleVenta;

import com.SolucionesInformaticasBA.minimarket.dto.request.DetalleVentaRequestDTO;
import com.SolucionesInformaticasBA.minimarket.dto.response.DetalleVentaResponseDTO;
import com.SolucionesInformaticasBA.minimarket.model.entity.Venta;
import com.SolucionesInformaticasBA.minimarket.model.entity.Producto;
@Component
public class DetalleVentaMapper {

    public DetalleVenta toEntity(DetalleVentaRequestDTO dto, Producto producto, Venta venta) {
        DetalleVenta d = new DetalleVenta();
        d.setProducto(producto);
        d.setVenta(venta);
        d.setCantidad(dto.getCantidad());
        return d;
    }

    public DetalleVentaResponseDTO toDTO(DetalleVenta d) {
        DetalleVentaResponseDTO dto = new DetalleVentaResponseDTO();

        if (d.getProducto() != null) {
            dto.setProductoId(d.getProducto().getId());
            dto.setNombre(d.getProducto().getNombre());
        } else {
            dto.setNombre(d.getNombreManual());
        }

        dto.setCantidad(d.getCantidad());
        dto.setPrecioUnitario(d.getPrecioUnitario());

        // 🔥 opcional pero recomendado
        dto.setSubtotal(
            d.getPrecioUnitario().multiply(BigDecimal.valueOf(d.getCantidad()))
        );

        return dto;
    }
}

