package com.SolucionesInformaticasBA.minimarket.mapper;

import com.SolucionesInformaticasBA.minimarket.repository.DetalleCompraRepository;
import com.SolucionesInformaticasBA.minimarket.repository.ProductoRepository;

import lombok.AllArgsConstructor;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.SolucionesInformaticasBA.minimarket.dto.request.DetalleCompraRequestDTO;
import com.SolucionesInformaticasBA.minimarket.dto.response.DetalleCompraResponseDTO;
import com.SolucionesInformaticasBA.minimarket.model.entity.Compra;
import com.SolucionesInformaticasBA.minimarket.model.entity.DetalleCompra;
import com.SolucionesInformaticasBA.minimarket.model.entity.Producto;

@Component
@AllArgsConstructor
public class DetalleCompraMapper {

    private final ProductoRepository productoRepository;

    public DetalleCompra toEntity(
            DetalleCompraRequestDTO dto,
            Producto producto,
            Compra compra
    ) {
        DetalleCompra d = new DetalleCompra();

        d.setIdProducto(producto.getId());
        d.setIdCompra(compra.getId());
        d.setCantidad(dto.getCantidad());
        d.setPrecioUnitario(dto.getPrecioUnitario());

        return d;
    }

    public DetalleCompraResponseDTO toDTO(DetalleCompra d) {
        Optional<Producto> p = productoRepository.findById(d.getIdProducto());
        DetalleCompraResponseDTO dto = new DetalleCompraResponseDTO();

        dto.setIdProducto(d.getIdProducto());
        dto.setNombreProducto(p.getNombre());
        dto.setCantidad(d.getCantidad());
        dto.setPrecioUnitario(d.getPrecioUnitario());

        return dto;
    }
}
