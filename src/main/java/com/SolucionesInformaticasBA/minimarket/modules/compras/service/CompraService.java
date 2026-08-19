package com.SolucionesInformaticasBA.minimarket.modules.compras.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

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
import com.SolucionesInformaticasBA.minimarket.modules.inventario.enums.EstadoLote;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.enums.TipoMovimiento;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.repository.LoteRepository;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.repository.MovimientoStockRepository;
import com.SolucionesInformaticasBA.minimarket.modules.productos.api.ProductosApi;
import com.SolucionesInformaticasBA.minimarket.modules.productos.api.dto.ProductoResponse;
import com.SolucionesInformaticasBA.minimarket.modules.proveedores.api.ProveedoresApi;
import com.SolucionesInformaticasBA.minimarket.modules.proveedores.api.dto.ProveedorResponse;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.api.UsuarioApi;
import com.SolucionesInformaticasBA.minimarket.shared.SecurityUtils;
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

        // Igual que en ventas: se guarda primero para poder referenciar la compra en cada
        // movimiento de stock y hacer reversible la anulación.
        Compra compra = compraRepository.save(toCompraEntity(request, idUsuario));

        List<DetalleCompra> detalles = new ArrayList<>();
        float total = 0;

        for (DetalleCompraRequest d : request.getDetalle()) {
            ProductoResponse producto = productosApi.getById(d.getIdProducto());

            DetalleCompra detalle = toDetalleCompraEntity(d, producto, compra);
            detalles.add(detalle);
            total += detalle.getTotal();

            if (producto.isManejaLotes()) {
                Lote lote = Lote.builder()
                    .idProducto(producto.getId())
                    .numeroLote(d.getNumeroLote())
                    .fechaVencimiento(d.getFechaVencimiento())
                    .cantidad(d.getCantidad())
                    // Sin esto el lote quedaba con estado NULL hasta que alguien listara lotes.
                    .estado(EstadoLote.calcularPara(d.getFechaVencimiento()))
                    .build();
                lote = loteRepository.save(lote);

                MovimientoStock m = MovimientoStock.builder()
                    .idProducto(producto.getId())
                    .idLote(lote.getId())
                    .cantidad(d.getCantidad())
                    .tipo(TipoMovimiento.COMPRA)
                    .motivo("Ingreso por compra")
                    .idUsuario(idUsuario)
                    .idReferencia(compra.getId())
                    .build();
                movimientoStockRepository.save(m);
            } else {
                inventarioApi.aumentar(MovimientoStockRequest.builder()
                    .idProducto(producto.getId())
                    .cantidad(d.getCantidad())
                    .tipo("COMPRA")
                    .motivo("Ingreso por compra")
                    .idUsuario(idUsuario)
                    .idReferencia(compra.getId())
                    .build());
            }
        }

        compra.setTotal(total);

        for (DetalleCompra d : detalles) {
            d.setIdCompra(compra.getId());
        }
        detalleCompraRepository.saveAll(detalles);

        // La sesión se resuelve acá y solo si se pagó de la caja; nunca se acepta del cliente.
        if (request.isPagoEnEfectivo()) {
            UUID idSesion = cajaApi.getIdSesionActiva();
            compra.setIdSesion(idSesion);
            cajaApi.registrarSalidaAutomatica(idSesion, idUsuario, total, "COMPRA", compra.getId());
        }

        compra = compraRepository.saveAndFlush(compra);

        return toCompraResponse(compra, toDetalleCompraResponseList(detalles));
    }

    public CompraResponse getById(UUID id) {
        Compra compra = compraRepository.findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> new ResourceNotFoundException("Compra no encontrada"));

        List<DetalleCompra> detalles = detalleCompraRepository.findByIdCompraAndDeletedAtIsNull(id);

        return toCompraResponse(compra, toDetalleCompraResponseList(detalles));
    }

    public List<CompraResponse> getAll() {
        return toCompraResponseList(
            compraRepository.findAll().stream().filter(c -> c.getDeletedAt() == null).toList());
    }

    public List<CompraResponse> getByUsuario(UUID idUsuario) {
        return toCompraResponseList(compraRepository.findByIdUsuarioAndDeletedAtIsNull(idUsuario));
    }

    public List<CompraResponse> getByFecha(LocalDateTime desde, LocalDateTime hasta) {
        return toCompraResponseList(compraRepository.findEnRango(desde, hasta));
    }

    /**
     * Anula una compra: saca del stock lo que había ingresado y, si se pagó de la caja,
     * devuelve la plata al turno. Falla si la mercadería ya se vendió o si el turno que
     * registró el pago ya cerró su corte.
     */
    @Transactional
    public void delete(UUID id) {
        Compra compra = compraRepository.findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> new ResourceNotFoundException("Compra no encontrada"));

        UUID idUsuario = SecurityUtils.getCurrentUserId();
        LocalDateTime ahora = LocalDateTime.now();

        revertirCaja(compra, idUsuario);
        revertirStock(compra, idUsuario);

        compra.setDeletedAt(ahora);
        compraRepository.save(compra);

        List<DetalleCompra> detalles = detalleCompraRepository.findByIdCompraAndDeletedAtIsNull(id);
        for (DetalleCompra d : detalles) {
            d.setDeletedAt(ahora);
        }
        detalleCompraRepository.saveAll(detalles);
    }

    /**
     * Devuelve la salida de caja al turno actual. Solo se permite si la compra se pagó en la
     * sesión que sigue abierta: un corte ya firmado no se toca.
     */
    private void revertirCaja(Compra compra, UUID idUsuario) {
        if (compra.getIdSesion() == null) {
            return;
        }
        UUID sesionActiva;
        try {
            sesionActiva = cajaApi.getIdSesionActiva();
        } catch (BadRequestException e) {
            throw new BadRequestException(
                "No se puede anular: la compra se pagó por caja y no hay un turno abierto "
                        + "donde devolver la plata");
        }
        if (!compra.getIdSesion().equals(sesionActiva)) {
            throw new BadRequestException(
                "No se puede anular: la compra se pagó en un turno de caja que ya fue cerrado");
        }
        cajaApi.registrarEntradaAutomatica(
            sesionActiva, idUsuario, compra.getTotal(), "REVERSA", compra.getId());
    }

    /** Saca del stock lo que ingresó la compra, usando los movimientos que la referencian. */
    private void revertirStock(Compra compra, UUID idUsuario) {
        List<MovimientoStock> movimientos =
            movimientoStockRepository.findByIdReferenciaAndTipoAndDeletedAtIsNull(
                compra.getId(), TipoMovimiento.COMPRA);

        for (MovimientoStock m : movimientos) {
            int aDescontar = Math.abs(m.getCantidad());
            if (aDescontar == 0) continue;

            if (m.getIdLote() != null) {
                Lote lote = loteRepository.findById(m.getIdLote())
                    .orElseThrow(() -> new BadRequestException(
                        "No se puede revertir la compra: falta el lote " + m.getIdLote()));

                if (lote.getCantidad() < aDescontar) {
                    throw new BadRequestException(
                        "No se puede anular: ya se vendió parte del lote " + lote.getNumeroLote());
                }
                lote.setCantidad(lote.getCantidad() - aDescontar);
                if (lote.getCantidad() == 0) {
                    lote.setDeletedAt(LocalDateTime.now());
                }
                loteRepository.save(lote);

                movimientoStockRepository.save(MovimientoStock.builder()
                    .idProducto(m.getIdProducto())
                    .idLote(lote.getId())
                    .cantidad(-aDescontar)
                    .tipo(TipoMovimiento.AJUSTE)
                    .motivo("Reversa por anulación de compra " + compra.getId())
                    .idUsuario(idUsuario)
                    .idReferencia(compra.getId())
                    .build());
            } else {
                // disminuir ya rechaza dejar el stock en negativo
                inventarioApi.disminuir(MovimientoStockRequest.builder()
                    .idProducto(m.getIdProducto())
                    .cantidad(aDescontar)
                    .tipo("AJUSTE")
                    .motivo("Reversa por anulación de compra " + compra.getId())
                    .idUsuario(idUsuario)
                    .idReferencia(compra.getId())
                    .build());
            }
        }
    }

    // Helpers

    /**
     * Igual que en ventas: los detalles de todas las compras se traen en una sola consulta y
     * se agrupan en memoria, en vez de un query por compra.
     */
    private List<CompraResponse> toCompraResponseList(List<Compra> compras) {
        if (compras.isEmpty()) {
            return List.of();
        }

        Map<UUID, List<DetalleCompra>> porCompra = detalleCompraRepository
            .findByIdCompraInAndDeletedAtIsNull(compras.stream().map(Compra::getId).toList())
            .stream()
            .collect(Collectors.groupingBy(DetalleCompra::getIdCompra));

        return compras.stream()
            .map(c -> toCompraResponse(c,
                toDetalleCompraResponseList(porCompra.getOrDefault(c.getId(), List.of()))))
            .toList();
    }

    private Compra toCompraEntity(CompraRequest request, UUID idUsuario) {
        return Compra.builder()
            .idUsuario(idUsuario)
            .total(0)
            .idProveedor(request.getIdProveedor())
            .tipoComprobante(request.getTipoComprobante())
            .nroComprobante(request.getNroComprobante())
            .observaciones(request.getObservaciones())
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
