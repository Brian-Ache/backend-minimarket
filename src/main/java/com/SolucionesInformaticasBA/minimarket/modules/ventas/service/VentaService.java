package com.SolucionesInformaticasBA.minimarket.modules.ventas.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.SolucionesInformaticasBA.minimarket.modules.inventario.api.InventarioApi;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.api.dto.MovimientoStockRequest;
import com.SolucionesInformaticasBA.minimarket.modules.productos.api.ProductosApi;
import com.SolucionesInformaticasBA.minimarket.modules.productos.api.dto.ProductoResponse;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.api.UsuarioApi;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.api.VentasApi;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.api.dto.DetalleVentaRequest;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.api.dto.DetalleVentaResponse;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.api.dto.VentaRequest;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.api.dto.VentaResponse;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.entity.DetalleVenta;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.entity.Venta;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.repository.DetalleVentaRepository;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.repository.VentaRepository;
import com.SolucionesInformaticasBA.minimarket.shared.exeption.BadRequestException;
import com.SolucionesInformaticasBA.minimarket.shared.exeption.ResourceNotFoundException;

import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;

@Service
@AllArgsConstructor
public class VentaService implements VentasApi {

    private final VentaRepository ventaRepository;
    private final DetalleVentaRepository detalleVentaRepository;
    private final UsuarioApi usuarioApi;
    private final ProductosApi productosApi;
    private final InventarioApi inventarioApi;

    @Override
    @Transactional
    public VentaResponse realizarVenta(UUID idUsuario, VentaRequest request) {
        if (!usuarioApi.existById(idUsuario)) {
            throw new ResourceNotFoundException("Usuario no encontrado");
        }

        if (request.getDetalles() == null || request.getDetalles().isEmpty()) {
            throw new BadRequestException("La venta debe tener al menos un detalle");
        }

        Venta venta = Venta.builder()
            .idUsuario(idUsuario)
            .build();

        List<DetalleVenta> detalles = new ArrayList<>();
        float total = 0;

        for (DetalleVentaRequest d : request.getDetalles()) {
            if (d.getCantidad() <= 0) {
                throw new BadRequestException("Cantidad inválida");
            }

            DetalleVenta detalle = new DetalleVenta();
            float precio;

            if ("PRODUCTO".equals(d.getTipo())) {
                if (d.getIdProducto() == null) {
                    throw new BadRequestException("idProducto requerido para tipo PRODUCTO");
                }

                ProductoResponse producto = productosApi.getById(d.getIdProducto());

                precio = producto.getPrecio();

                detalle.setIdProducto(producto.getId());
                detalle.setNombreProducto(producto.getNombre());

                inventarioApi.disminuir(MovimientoStockRequest.builder()
                    .idProducto(producto.getId())
                    .cantidad(d.getCantidad())
                    .tipo("VENTA")
                    .motivo("Venta realizada")
                    .idUsuario(idUsuario)
                    .build());

            } else if ("MANUAL".equals(d.getTipo())) {
                if (d.getNombreManual() == null || d.getNombreManual().isBlank()) {
                    throw new BadRequestException("nombreManual requerido para tipo MANUAL");
                }

                if (d.getPrecioUnitario() <= 0) {
                    throw new BadRequestException("precioUnitario debe ser mayor a 0 para tipo MANUAL");
                }

                precio = d.getPrecioUnitario();

                detalle.setIdProducto(null);
                detalle.setNombreProducto(d.getNombreManual());

            } else {
                throw new BadRequestException("Tipo de detalle inválido: " + d.getTipo());
            }

            detalle.setCantidad(d.getCantidad());
            detalle.setPrecioUnitario(precio);

            detalles.add(detalle);

            total += precio * d.getCantidad();
        }

        venta.setTotal(total);
        venta = ventaRepository.save(venta);

        for (DetalleVenta d : detalles) {
            d.setIdVenta(venta.getId());
        }
        detalleVentaRepository.saveAll(detalles);

        return toVentaResponse(venta, toDetalleVentaResponseList(detalles));
    }

    @Override
    public VentaResponse getById(UUID id) {
        Venta venta = ventaRepository.findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> new ResourceNotFoundException("Venta no encontrada"));

        List<DetalleVenta> detalles = detalleVentaRepository.findByIdVentaAndDeletedAtIsNull(id);

        return toVentaResponse(venta, toDetalleVentaResponseList(detalles));
    }

    @Override
    public List<VentaResponse> getAll() {
        return ventaRepository.findAll().stream()
            .filter(v -> v.getDeletedAt() == null)
            .map(v -> {
                List<DetalleVenta> detalles = detalleVentaRepository.findByIdVentaAndDeletedAtIsNull(v.getId());
                return toVentaResponse(v, toDetalleVentaResponseList(detalles));
            })
            .toList();
    }

    @Override
    public List<VentaResponse> getByUsuario(UUID idUsuario) {
        return ventaRepository.findByIdUsuarioAndDeletedAtIsNull(idUsuario).stream()
            .map(v -> {
                List<DetalleVenta> detalles = detalleVentaRepository.findByIdVentaAndDeletedAtIsNull(v.getId());
                return toVentaResponse(v, toDetalleVentaResponseList(detalles));
            })
            .toList();
    }

    @Override
    public List<VentaResponse> getByFecha(LocalDateTime desde, LocalDateTime hasta) {
        return ventaRepository.findByCreatedAtBetweenAndDeletedAtIsNull(desde, hasta).stream()
            .map(v -> {
                List<DetalleVenta> detalles = detalleVentaRepository.findByIdVentaAndDeletedAtIsNull(v.getId());
                return toVentaResponse(v, toDetalleVentaResponseList(detalles));
            })
            .toList();
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        Venta venta = ventaRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Venta no encontrada"));

        venta.setDeletedAt(LocalDateTime.now());
        ventaRepository.save(venta);

        List<DetalleVenta> detalles = detalleVentaRepository.findByIdVentaAndDeletedAtIsNull(id);
        for (DetalleVenta d : detalles) {
            d.setDeletedAt(LocalDateTime.now());
        }
        detalleVentaRepository.saveAll(detalles);
    }

    // Helpers

    private VentaResponse toVentaResponse(Venta venta, List<DetalleVentaResponse> detalles) {
        VentaResponse response = new VentaResponse();
        response.setId(venta.getId());
        response.setFecha(venta.getCreatedAt());
        response.setTotal(venta.getTotal());
        response.setDetalles(detalles);
        return response;
    }

    private DetalleVentaResponse toDetalleVentaResponse(DetalleVenta detalle) {
        DetalleVentaResponse response = new DetalleVentaResponse();

        response.setNombre(detalle.getNombreProducto());

        if (detalle.getIdProducto() != null) {
            response.setIdProducto(detalle.getIdProducto());
            response.setTipo("PRODUCTO");
        } else {
            response.setTipo("MANUAL");
        }
        response.setCantidad(detalle.getCantidad());
        response.setPrecioUnitario(detalle.getPrecioUnitario());
        response.setSubtotal(detalle.getPrecioUnitario() * detalle.getCantidad());

        return response;
    }

    private List<DetalleVentaResponse> toDetalleVentaResponseList(List<DetalleVenta> detalles) {
        return detalles.stream()
            .map(this::toDetalleVentaResponse)
            .toList();
    }
}
