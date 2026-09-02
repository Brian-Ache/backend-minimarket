package com.SolucionesInformaticasBA.minimarket.modules.compras.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.SolucionesInformaticasBA.minimarket.modules.caja.api.CajaApi;
import com.SolucionesInformaticasBA.minimarket.modules.compras.api.CompraApi;
import com.SolucionesInformaticasBA.minimarket.modules.compras.api.dto.*;
import com.SolucionesInformaticasBA.minimarket.modules.compras.entity.*;
import com.SolucionesInformaticasBA.minimarket.modules.compras.repository.*;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.api.InventarioApi;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.api.dto.MovimientoStockRequest;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.entity.Lote;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.entity.MovimientoStock;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.enums.TipoMovimiento;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.repository.LoteRepository;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.repository.MovimientoStockRepository;
import com.SolucionesInformaticasBA.minimarket.modules.productos.api.ProductosApi;
import com.SolucionesInformaticasBA.minimarket.modules.productos.api.dto.ProductoResponse;
import com.SolucionesInformaticasBA.minimarket.modules.productos.entity.Producto;
import com.SolucionesInformaticasBA.minimarket.modules.productos.repository.ProductoRepository;
import com.SolucionesInformaticasBA.minimarket.modules.proveedores.api.ProveedoresApi;
import com.SolucionesInformaticasBA.minimarket.modules.proveedores.api.dto.ProveedorResponse;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.api.UsuarioApi;
import com.SolucionesInformaticasBA.minimarket.shared.exeption.BadRequestException;
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
    private final ProductoRepository productoRepository;
    private final InventarioApi inventarioApi;
    private final LoteRepository loteRepository;
    private final MovimientoStockRepository movimientoStockRepository;
    private final ProveedoresApi proveedoresApi;
    private final CajaApi cajaApi;

    @Transactional
    public CompraResponse crear(UUID idUsuario, CompraRequest request) {
        if (!usuarioApi.existById(idUsuario)) {
            throw new ResourceNotFoundException("Usuario no encontrado");
        }

        if (request.getIdProveedor() != null && !proveedoresApi.existsById(request.getIdProveedor())) {
            throw new BadRequestException("El proveedor especificado no existe");
        }

        Compra compra = toCompraEntity(request, idUsuario);

        List<DetalleCompra> detalles = new ArrayList<>();
        float total = 0;

        for (DetalleCompraRequest d : request.getDetalle()) {
            Producto producto = productoRepository.findByIdAndDeletedAtIsNull(d.getIdProducto());
            if (producto == null) {
                throw new ResourceNotFoundException("Producto no encontrado: " + d.getIdProducto());
            }

            // Actualizar costo, margen, precio y proveedor del producto
            producto.setCosto(d.getPrecioUnitario());
            if (d.getMargen() != null) producto.setMargen(d.getMargen());
            if (d.getPrecioVenta() != null) producto.setPrecio(d.getPrecioVenta());
            if (request.getIdProveedor() != null) {
                producto.setIdProveedor(request.getIdProveedor());
            }
            productoRepository.save(producto);

            DetalleCompra detalle = toDetalleCompraEntity(d, producto, compra);
            detalles.add(detalle);
            total += detalle.getTotal();

            if (producto.isManejaLotes()) {
                Lote lote = Lote.builder()
                    .idProducto(producto.getId())
                    .numeroLote(d.getNumeroLote())
                    .fechaVencimiento(d.getFechaVencimiento())
                    .cantidad(d.getCantidad())
                    .build();
                lote = loteRepository.save(lote);

                MovimientoStock m = MovimientoStock.builder()
                    .idProducto(producto.getId())
                    .idLote(lote.getId())
                    .cantidad(d.getCantidad())
                    .tipo(TipoMovimiento.COMPRA)
                    .motivo("Ingreso por compra")
                    .idUsuario(idUsuario)
                    .build();
                movimientoStockRepository.save(m);
            } else {
                inventarioApi.aumentar(MovimientoStockRequest.builder()
                    .idProducto(producto.getId())
                    .cantidad(d.getCantidad())
                    .tipo("COMPRA")
                    .motivo("Ingreso por compra")
                    .idUsuario(idUsuario)
                    .build());
            }
        }

        compra.setTotal(total);
        compra = compraRepository.save(compra);

        for (DetalleCompra d : detalles) {
            d.setIdCompra(compra.getId());
        }
        detalleCompraRepository.saveAll(detalles);

        if (request.getIdSesion() != null) {
            cajaApi.registrarSalidaAutomatica(request.getIdSesion(), idUsuario, total, "COMPRA", compra.getId());
        }

        return toCompraResponse(compra, toDetalleCompraResponseList(detalles));
    }

    public CompraResponse getById(UUID id) {
        Compra compra = compraRepository.findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> new ResourceNotFoundException("Compra no encontrada"));

        List<DetalleCompra> detalles = detalleCompraRepository.findByIdCompraAndDeletedAtIsNull(id);

        return toCompraResponse(compra, toDetalleCompraResponseList(detalles));
    }

    public Page<CompraResponse> getAllFiltered(UUID idProveedor, String tipoComprobante,
                                               LocalDateTime desde, LocalDateTime hasta,
                                               Pageable pageable) {
        return compraRepository.findAllFiltered(idProveedor, tipoComprobante, desde, hasta, pageable)
            .map(c -> {
                List<DetalleCompra> detalles = detalleCompraRepository.findByIdCompraAndDeletedAtIsNull(c.getId());
                return toCompraResponse(c, toDetalleCompraResponseList(detalles));
            });
    }

    // MÉTODOS COMENTADOS: Se reemplazaron por getAllFiltered() que cubre todos los casos
    // con un solo query parametrizado. Se mantienen comentados por si en el futuro
    // se necesitan endpoints dedicados (ej: historial por un usuario específico).

    // public Page<CompraResponse> getAll(Pageable pageable) {
    //     return compraRepository.findAllPaginated(pageable).map(c -> {
    //         List<DetalleCompra> detalles = detalleCompraRepository.findByIdCompraAndDeletedAtIsNull(c.getId());
    //         return toCompraResponse(c, toDetalleCompraResponseList(detalles));
    //     });
    // }

    // public Page<CompraResponse> getByUsuario(UUID idUsuario, Pageable pageable) {
    //     return compraRepository.findByIdUsuarioAndDeletedAtIsNull(idUsuario, pageable)
    //         .map(c -> {
    //             List<DetalleCompra> detalles = detalleCompraRepository.findByIdCompraAndDeletedAtIsNull(c.getId());
    //             return toCompraResponse(c, toDetalleCompraResponseList(detalles));
    //         });
    // }

    // public Page<CompraResponse> getByFecha(LocalDateTime desde, LocalDateTime hasta, Pageable pageable) {
    //     return compraRepository.findByCreatedAtBetweenAndDeletedAtIsNull(desde, hasta, pageable)
    //         .map(c -> {
    //             List<DetalleCompra> detalles = detalleCompraRepository.findByIdCompraAndDeletedAtIsNull(c.getId());
    //             return toCompraResponse(c, toDetalleCompraResponseList(detalles));
    //         });
    // }

    @Transactional
    public void delete(UUID id, UUID idUsuario) {
        Compra compra = compraRepository.findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> new ResourceNotFoundException("Compra no encontrada"));

        List<DetalleCompra> detalles = detalleCompraRepository.findByIdCompraAndDeletedAtIsNull(id);

        // Revertir stock por cada producto
        for (DetalleCompra d : detalles) {
            Producto producto = productoRepository.findByIdAndDeletedAtIsNull(d.getIdProducto());
            if (producto == null) {
                System.out.println("WARNING: Producto no encontrado (" + d.getIdProducto()
                    + "), no se revirtió stock para el detalle " + d.getId());
                continue;
            }

            if (producto.isManejaLotes()) {
                // Buscar lote creado para esta compra y hacerle soft delete
                List<Lote> lotes = loteRepository.findByIdProducto(d.getIdProducto());
                for (Lote lote : lotes) {
                    if (lote.getDeletedAt() == null && lote.getCantidad() == d.getCantidad()) {
                        lote.setDeletedAt(java.time.LocalDateTime.now());
                        loteRepository.save(lote);

                        // Buscar y soft-delete el movimiento de stock asociado
                        List<MovimientoStock> movimientos = movimientoStockRepository
                            .findByIdProductoAndDeletedAtIsNullOrderByCreatedAtDesc(d.getIdProducto());
                        for (MovimientoStock m : movimientos) {
                            if (m.getIdLote() != null && m.getIdLote().equals(lote.getId())
                                && m.getTipo() == TipoMovimiento.COMPRA) {
                                m.setDeletedAt(java.time.LocalDateTime.now());
                                movimientoStockRepository.save(m);
                                break;
                            }
                        }
                        break;
                    }
                }
            } else {
                // No maneja lotes: disminuir stock
                inventarioApi.disminuir(MovimientoStockRequest.builder()
                    .idProducto(d.getIdProducto())
                    .cantidad(d.getCantidad())
                    .tipo("COMPRA")
                    .motivo("Reversión por eliminación de compra")
                    .idUsuario(idUsuario)
                    .build());
            }
        }

        // Soft delete compra + detalles
        compra.setDeletedAt(java.time.LocalDateTime.now());
        compraRepository.save(compra);

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
            .idProveedor(request.getIdProveedor())
            .tipoComprobante(request.getTipoComprobante())
            .nroComprobante(request.getNroComprobante())
            .observaciones(request.getObservaciones())
            .idSesion(request.getIdSesion())
            .build();
    }

    private CompraResponse toCompraResponse(Compra compra, List<DetalleCompraResponse> detalle) {
        ProveedorResponse proveedor = null;
        if (compra.getIdProveedor() != null) {
            try {
                proveedor = proveedoresApi.getById(compra.getIdProveedor());
            } catch (ResourceNotFoundException e) {
                proveedor = null;
            }
        }

        return CompraResponse.builder()
            .id(compra.getId())
            .fecha(compra.getCreatedAt())
            .total(compra.getTotal())
            .detalle(detalle)
            .proveedor(proveedor)
            .tipoComprobante(compra.getTipoComprobante())
            .nroComprobante(compra.getNroComprobante())
            .observaciones(compra.getObservaciones())
            .build();
    }

    private DetalleCompra toDetalleCompraEntity(DetalleCompraRequest request, Producto producto, Compra compra) {
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
            .barcode(detalle.getBarcode())
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
