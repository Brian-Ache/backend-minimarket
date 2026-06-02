package com.SolucionesInformaticasBA.minimarket.mapper;

import org.springframework.stereotype.Component;

import com.SolucionesInformaticasBA.minimarket.dto.request.DetalleCompraRequestDTO;
import com.SolucionesInformaticasBA.minimarket.dto.response.DetalleCompraResponseDTO;
import com.SolucionesInformaticasBA.minimarket.model.entity.Compra;
import com.SolucionesInformaticasBA.minimarket.model.entity.DetalleCompra;
import com.SolucionesInformaticasBA.minimarket.model.entity.Producto;

@Component
public class DetalleCompraMapper {

    public DetalleCompra toEntity(
            DetalleCompraRequestDTO dto,
            Producto producto,
            Compra compra
    ) {
        DetalleCompra d = new DetalleCompra();

        d.setProducto(producto);
        d.setCompra(compra);
        d.setCantidad(dto.getCantidad());
        d.setPrecioUnitario(dto.getPrecioUnitario());

        return d;
    }

    public DetalleCompraResponseDTO toDTO(DetalleCompra d) {
        DetalleCompraResponseDTO dto = new DetalleCompraResponseDTO();

        dto.setProductoId(d.getProducto().getId());
        dto.setNombreProducto(d.getProducto().getNombre());
        dto.setCantidad(d.getCantidad());
        dto.setPrecioUnitario(d.getPrecioUnitario());

        return dto;
    }
}
