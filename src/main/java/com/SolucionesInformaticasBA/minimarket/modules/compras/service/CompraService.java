package com.SolucionesInformaticasBA.minimarket.modules.compras.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
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
import com.SolucionesInformaticasBA.minimarket.modules.inventario.enums.EstadoLote;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.enums.TipoMovimiento;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.repository.LoteRepository;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.repository.MovimientoStockRepository;
import com.SolucionesInformaticasBA.minimarket.modules.productos.api.ProductosApi;
import com.SolucionesInformaticasBA.minimarket.modules.productos.entity.Producto;
import com.SolucionesInformaticasBA.minimarket.modules.productos.repository.ProductoRepository;
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

        // Igual que en ventas: se guarda primero para poder referenciar la compra en cada
        // movimiento de stock y hacer reversible la anulación.
        Compra compra = compraRepository.save(toCompraEntity(request, idUsuario));

        // Igual que en ventas: se procesa por orden de bloqueo y cada línea vuelve a su
        // posición, así lo que se guarda y se devuelve sigue el orden en que se cargó.
        List<DetalleCompraRequest> pedidos = request.getDetalle();
        DetalleCompra[] procesados = new DetalleCompra[pedidos.size()];

        for (int i : ordenDeBloqueo(pedidos)) {
            DetalleCompraRequest d = pedidos.get(i);
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

            procesados[i] = toDetalleCompraEntity(d, producto, compra);

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

        List<DetalleCompra> detalles = new ArrayList<>(Arrays.asList(procesados));

        // El total se suma en el orden en que se cargó la compra y no en el de bloqueo: con
        // float, cambiar el orden de la suma puede correr el último centavo.
        float total = 0;
        for (DetalleCompra d : detalles) {
            total += d.getTotal();
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

    /**
     * Búsqueda paginada con todos los filtros opcionales. Los detalles de la página se traen
     * con {@link #toCompraResponseList}, en una consulta y no una por compra.
     */
    public Page<CompraResponse> getAllFiltered(UUID idProveedor, String tipoComprobante,
                                               LocalDateTime desde, LocalDateTime hasta,
                                               Pageable pageable) {
        Page<Compra> compras =
            compraRepository.findAllFiltered(idProveedor, tipoComprobante, desde, hasta, pageable);

        return new PageImpl<>(
            toCompraResponseList(compras.getContent()), pageable, compras.getTotalElements());
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

    /**
     * Anula una compra: saca del stock lo que había ingresado y, si se pagó de la caja,
     * devuelve la plata al turno. Falla si la mercadería ya se vendió o si el turno que
     * registró el pago ya cerró su corte.
     *
     * <p><b>No revierte el costo, el margen, el precio ni el proveedor que el alta le escribió
     * al producto</b>, y es a propósito: una compra se anula por muchos motivos —el proveedor
     * no entregó, se cargó dos veces, se devolvió la mercadería— y en ninguno de esos el precio
     * de venta vigente tiene por qué volver atrás. Si lo que estaba mal era justamente el
     * precio, se corrige por el ABM de productos, que es donde vive esa decisión.
     */
    @Transactional
    public void delete(UUID id) {
        Compra compra = compraRepository.findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> new ResourceNotFoundException("Compra no encontrada"));

        // Del JWT y no de un parámetro: antes venía en un header que mandaba el cliente, así
        // que quien anulaba podía firmar la reversa de stock y la entrada de caja con el id de
        // otro usuario, justo en las dos tablas que sirven para auditar. Mismo criterio que la
        // anulación de ventas.
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
        List<MovimientoStock> movimientos = new ArrayList<>(
            movimientoStockRepository.findByIdReferenciaAndTipoAndDeletedAtIsNull(
                compra.getId(), TipoMovimiento.COMPRA));

        // Mismo orden de bloqueo que el alta: por producto ascendente y, dentro de cada uno,
        // los lotes en el orden que impone findParaDescuentoFifo.
        movimientos.sort(Comparator.comparing(MovimientoStock::getIdProducto,
                Comparator.nullsLast(Comparator.naturalOrder())));
        reservarLotes(movimientos);

        for (MovimientoStock m : movimientos) {
            int aDescontar = Math.abs(m.getCantidad());
            if (aDescontar == 0) continue;

            if (m.getIdLote() != null) {
                Lote lote = loteRepository.findByIdParaActualizar(m.getIdLote())
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
                try {
                    // disminuir ya rechaza dejar el stock en negativo
                    inventarioApi.disminuir(MovimientoStockRequest.builder()
                        .idProducto(m.getIdProducto())
                        .cantidad(aDescontar)
                        .tipo("AJUSTE")
                        .motivo("Reversa por anulación de compra " + compra.getId())
                        .idUsuario(idUsuario)
                        .idReferencia(compra.getId())
                        .build());
                } catch (BadRequestException e) {
                    // "Stock insuficiente" es correcto pero desorienta cuando lo que estás
                    // haciendo es anular: significa que la mercadería ya se vendió. Se explica
                    // igual que en la rama de lotes, y se conserva el detalle de cantidades.
                    throw new BadRequestException(
                        "No se puede anular: ya se vendió parte de lo que ingresó esta compra ("
                            + nombreDeProducto(m.getIdProducto()) + "). " + e.getMessage());
                }
            }
        }
    }

    // Helpers

    /** Solo para armar mensajes de error: no se paga la consulta en el camino feliz. */
    private String nombreDeProducto(UUID idProducto) {
        String nombre = productosApi.getNombresPorId(List.of(idProducto)).get(idProducto);
        return nombre != null ? nombre : "producto " + idProducto;
    }

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
                // Incluyendo bajas: dar de baja a un proveedor cierra las compras nuevas, no
                // borra de la pantalla a quién se le compró. Con getById, cada baja dejaba
                // todas sus compras históricas mostrando proveedor en null.
                proveedor = proveedoresApi.getByIdIncluyendoBajas(compra.getIdProveedor());
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

    /**
     * Índices del detalle ordenados por idProducto ascendente. Es el mismo criterio que usa
     * ventas: toda operación que bloquee filas de inventario las toma en este orden, para que
     * dos transacciones sobre los mismos productos no se queden cada una con la fila que la
     * otra necesita y la base tenga que matar una por deadlock.
     */
    private static List<Integer> ordenDeBloqueo(List<DetalleCompraRequest> detalles) {
        return IntStream.range(0, detalles.size())
            .boxed()
            .sorted(Comparator.comparing((Integer i) -> detalles.get(i).getIdProducto(),
                    Comparator.nullsLast(Comparator.naturalOrder())))
            .toList();
    }

    /**
     * Toma por adelantado el lock de los lotes de cada producto involucrado, en el orden del
     * FIFO. La reversa recorre movimientos, o sea un lote suelto por vez; sin esta pasada
     * previa bloquearía los lotes de un producto en un orden distinto al del resto del
     * sistema, que es justo lo que abre el ciclo.
     */
    private void reservarLotes(List<MovimientoStock> movimientos) {
        movimientos.stream()
            .filter(m -> m.getIdLote() != null)
            .map(MovimientoStock::getIdProducto)
            .distinct()
            .forEach(loteRepository::findParaDescuentoFifo);
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
