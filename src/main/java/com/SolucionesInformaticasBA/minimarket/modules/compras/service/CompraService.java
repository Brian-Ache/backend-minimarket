package com.SolucionesInformaticasBA.minimarket.modules.compras.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.SolucionesInformaticasBA.minimarket.modules.compras.api.CompraApi;
import com.SolucionesInformaticasBA.minimarket.modules.compras.api.dto.*;
import com.SolucionesInformaticasBA.minimarket.modules.compras.entity.*;
import com.SolucionesInformaticasBA.minimarket.modules.compras.repository.*;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.api.InventarioApi;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.api.dto.MovimientoStockRequest;
import com.SolucionesInformaticasBA.minimarket.modules.productos.api.ProductosApi;
import com.SolucionesInformaticasBA.minimarket.modules.productos.api.dto.ProductoResponse;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.api.UsuarioApi;
import com.SolucionesInformaticasBA.minimarket.shared.exeption.ResourceNotFoundException;

import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;

@Service
@AllArgsConstructor
public class CompraService implements CompraApi {
    private final CompraRepository compraRepository;
    private final DetalleCompraRepository detalleCompraRepository;
    private final UsuarioApi usuarioApi;
    private final ProductosApi productosApi;
    private final InventarioApi inventarioApi;

    @Transactional
    public CompraResponse crear(UUID idUsuario, CompraRequest request) {
        if (!usuarioApi.existById(idUsuario)) {
            throw new ResourceNotFoundException("Usuario no encontrado");
        }

        Compra compra = toCompraEntity(request, idUsuario);

        List<DetalleCompra> detalles = new ArrayList<>();
        float total = 0;

        for (DetalleCompraRequest d : request.getDetalle()) {
            ProductoResponse producto = productosApi.getById(d.getIdProducto());

            DetalleCompra detalle = toDetalleCompraEntity(d, producto, compra);
            detalles.add(detalle);
            total += detalle.getTotal();

            inventarioApi.aumentar(MovimientoStockRequest.builder()
                .idProducto(producto.getId())
                .cantidad(d.getCantidad())
                .tipo("COMPRA")
                .motivo("Ingreso por compra")
                .idUsuario(idUsuario)
                .build());
        }

        compra.setTotal(total);
        compra = compraRepository.save(compra);

        for (DetalleCompra d : detalles) {
            d.setIdCompra(compra.getId());
        }
        detalleCompraRepository.saveAll(detalles);

        return toCompraResponse(compra, toDetalleCompraResponseList(detalles));
    }

    public CompraResponse getById(UUID id) {
        Compra compra = compraRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Compra no encontrada"));

        if (compra.getDeletedAt() != null) {
            throw new ResourceNotFoundException("Compra no encontrada");
        }

        List<DetalleCompra> detalles = detalleCompraRepository.findByIdCompraAndDeletedAtIsNull(id);

        return toCompraResponse(compra, toDetalleCompraResponseList(detalles));
    }

    public List<CompraResponse> getAll() {
        return compraRepository.findAll().stream()
            .filter(c -> c.getDeletedAt() == null)
            .map(c -> {
                List<DetalleCompra> detalles = detalleCompraRepository.findByIdCompraAndDeletedAtIsNull(c.getId());
                return toCompraResponse(c, toDetalleCompraResponseList(detalles));
            })
            .toList();
    }

    public List<CompraResponse> getByUsuario(UUID idUsuario) {
        return compraRepository.findByIdUsuarioAndDeletedAtIsNull(idUsuario).stream()
            .map(c -> {
                List<DetalleCompra> detalles = detalleCompraRepository.findByIdCompraAndDeletedAtIsNull(c.getId());
                return toCompraResponse(c, toDetalleCompraResponseList(detalles));
            })
            .toList();
    }

    public List<CompraResponse> getByFecha(LocalDateTime desde, LocalDateTime hasta) {
        return compraRepository.findByCreatedAtBetweenAndDeletedAtIsNull(desde, hasta).stream()
            .map(c -> {
                List<DetalleCompra> detalles = detalleCompraRepository.findByIdCompraAndDeletedAtIsNull(c.getId());
                return toCompraResponse(c, toDetalleCompraResponseList(detalles));
            })
            .toList();
    }

    @Transactional
    public void delete(UUID id) {
        Compra compra = compraRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Compra no encontrada"));

        compra.setDeletedAt(java.time.LocalDateTime.now());
        compraRepository.save(compra);

        List<DetalleCompra> detalles = detalleCompraRepository.findByIdCompraAndDeletedAtIsNull(id);
        for (DetalleCompra d : detalles) {
            d.setDeletedAt(java.time.LocalDateTime.now());
        }
        detalleCompraRepository.saveAll(detalles);
    }

    // Helpers

    private Compra toCompraEntity(CompraRequest request, UUID idUsuario) {
        return Compra.builder()
            .idUsuario(idUsuario)
            .total(0)
            .build();
    }

    private CompraResponse toCompraResponse(Compra compra, List<DetalleCompraResponse> detalle) {
        return CompraResponse.builder()
            .id(compra.getId())
            .fecha(compra.getCreatedAt())
            .total(compra.getTotal())
            .detalle(detalle)
            .build();
    }

    private DetalleCompra toDetalleCompraEntity(DetalleCompraRequest request, ProductoResponse producto, Compra compra) {
        float subtotal = request.getPrecioUnitario() * request.getCantidad();
        return DetalleCompra.builder()
            .idCompra(compra.getId())
            .idProducto(producto.getId())
            .nombreProducto(producto.getNombre())
            .barcode(producto.getBarcode())
            .precioUnitario(request.getPrecioUnitario())
            .cantidad(request.getCantidad())
            .total(subtotal)
            .build();
    }

    private DetalleCompraResponse toDetalleCompraResponse(DetalleCompra detalle) {
        return DetalleCompraResponse.builder()
            .idCompra(detalle.getIdCompra())
            .idProducto(detalle.getIdProducto())
            .nombreProducto(detalle.getNombreProducto())
            .batcode(detalle.getBarcode())
            .cantidad(detalle.getCantidad())
            .precioUnitario(detalle.getPrecioUnitario())
            .total(detalle.getTotal())
            .build();
    }

    private List<DetalleCompraResponse> toDetalleCompraResponseList(List<DetalleCompra> detalles) {
        return detalles.stream()
            .map(this::toDetalleCompraResponse)
            .toList();
    }

}
