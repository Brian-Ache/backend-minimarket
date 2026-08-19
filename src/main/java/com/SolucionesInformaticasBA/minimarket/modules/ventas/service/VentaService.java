package com.SolucionesInformaticasBA.minimarket.modules.ventas.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.SolucionesInformaticasBA.minimarket.modules.caja.api.CajaApi;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.api.InventarioApi;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.api.dto.MovimientoStockRequest;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.entity.Lote;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.entity.MovimientoStock;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.enums.TipoMovimiento;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.repository.LoteRepository;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.repository.MovimientoStockRepository;
import com.SolucionesInformaticasBA.minimarket.modules.productos.api.ProductosApi;
import com.SolucionesInformaticasBA.minimarket.modules.productos.api.dto.ProductoResponse;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.api.UsuarioApi;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.api.VentasApi;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.api.dto.CobrarVentaRequest;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.api.dto.CobrarVentaResponse;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.api.dto.DetalleVentaRequest;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.api.dto.DetalleVentaResponse;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.api.dto.ResumenDiarioResponse;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.api.dto.VentaRequest;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.api.dto.VentaResponse;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.entity.DetalleVenta;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.entity.Venta;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.repository.DetalleVentaRepository;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.repository.VentaRepository;
import com.SolucionesInformaticasBA.minimarket.shared.SecurityUtils;
import com.SolucionesInformaticasBA.minimarket.shared.exeption.BadRequestException;
import com.SolucionesInformaticasBA.minimarket.shared.exeption.ResourceNotFoundException;

import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;

@Service
@AllArgsConstructor
public class VentaService implements VentasApi {

    private static final String ES_EFECTIVO = "EFECTIVO";

    private final VentaRepository ventaRepository;
    private final DetalleVentaRepository detalleVentaRepository;
    private final UsuarioApi usuarioApi;
    private final ProductosApi productosApi;
    private final InventarioApi inventarioApi;
    private final LoteRepository loteRepository;
    private final MovimientoStockRepository movimientoStockRepository;
    private final CajaApi cajaApi;

    @Override
    @Transactional
    public VentaResponse realizarVenta(UUID idUsuario, VentaRequest request) {
        if (!usuarioApi.existById(idUsuario)) {
            throw new ResourceNotFoundException("Usuario no encontrado");
        }

        if (request.getDetalles() == null || request.getDetalles().isEmpty()) {
            throw new BadRequestException("La venta debe tener al menos un detalle");
        }

        // Se guarda primero para tener el id: cada movimiento de stock lo referencia y así
        // la anulación puede revertir exactamente lo que esta venta descontó.
        Venta venta = ventaRepository.save(Venta.builder()
            .idUsuario(idUsuario)
            .total(0)
            .build());

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
                // Se congela el costo de hoy: si mañana cambia, la ganancia histórica no se
                // reescribe. Los ítems MANUAL quedan sin costo (null), no en 0.
                detalle.setCostoUnitario(producto.getCosto());

                if (producto.isManejaLotes()) {
                    int cantidadRestante = d.getCantidad();
                    List<Lote> lotes = loteRepository.findByIdProductoAndDeletedAtIsNullOrderByFechaVencimientoAsc(producto.getId());
                    for (Lote lote : lotes) {
                        if (cantidadRestante <= 0) break;
                        if (lote.getCantidad() <= 0) continue;

                        int descontar = Math.min(lote.getCantidad(), cantidadRestante);
                        lote.setCantidad(lote.getCantidad() - descontar);
                        loteRepository.save(lote);
                        cantidadRestante -= descontar;

                        MovimientoStock m = MovimientoStock.builder()
                            .idProducto(producto.getId())
                            .idLote(lote.getId())
                            .cantidad(-descontar)
                            .tipo(TipoMovimiento.VENTA)
                            .motivo("Venta realizada (FIFO)")
                            .idUsuario(idUsuario)
                            .idReferencia(venta.getId())
                            .build();
                        movimientoStockRepository.save(m);
                    }
                    if (cantidadRestante > 0) {
                        throw new BadRequestException("Stock insuficiente en lotes para el producto " + producto.getNombre());
                    }
                } else {
                    inventarioApi.disminuir(MovimientoStockRequest.builder()
                        .idProducto(producto.getId())
                        .cantidad(d.getCantidad())
                        .tipo("VENTA")
                        .motivo("Venta realizada")
                        .idUsuario(idUsuario)
                        .idReferencia(venta.getId())
                        .build());
                }

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
        venta = ventaRepository.saveAndFlush(venta);

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
        return toVentaResponseList(
            ventaRepository.findAll().stream().filter(v -> v.getDeletedAt() == null).toList());
    }

    @Override
    public List<VentaResponse> getByUsuario(UUID idUsuario) {
        return toVentaResponseList(ventaRepository.findByIdUsuarioAndDeletedAtIsNull(idUsuario));
    }

    @Override
    public List<VentaResponse> getByFechaCobradas(LocalDateTime desde, LocalDateTime hasta) {
        return toVentaResponseList(ventaRepository.findCobradasEnRango(desde, hasta));
    }

    @Override
    public List<VentaResponse> getByFecha(LocalDateTime desde, LocalDateTime hasta) {
        return toVentaResponseList(ventaRepository.findEnRango(desde, hasta));
    }

    /**
     * Anula una venta no cobrada y devuelve la mercadería al stock.
     *
     * <p>Una venta ya cobrada no se anula: movió plata y puede estar dentro de un corte
     * cerrado. Para eso corresponde un flujo de devolución, que hoy no existe.
     */
    @Override
    @Transactional
    public void delete(UUID id) {
        Venta venta = ventaRepository.findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> new ResourceNotFoundException("Venta no encontrada"));

        if (Boolean.TRUE.equals(venta.getCobrada())) {
            throw new BadRequestException(
                "No se puede anular una venta ya cobrada. Registrá una devolución.");
        }

        UUID idUsuario = SecurityUtils.getCurrentUserId();
        LocalDateTime ahora = LocalDateTime.now();

        revertirStock(venta, idUsuario);

        venta.setDeletedAt(ahora);
        ventaRepository.save(venta);

        List<DetalleVenta> detalles = detalleVentaRepository.findByIdVentaAndDeletedAtIsNull(id);
        for (DetalleVenta d : detalles) {
            d.setDeletedAt(ahora);
        }
        detalleVentaRepository.saveAll(detalles);
    }

    /**
     * Devuelve al stock lo que descontó la venta, apoyándose en los movimientos que la
     * referencian. Trabajar sobre los movimientos —y no sobre los detalles— es lo que permite
     * reponer cada lote exactamente en la cantidad de la que se sacó cuando el FIFO repartió
     * una línea entre varios lotes.
     *
     * <p>Los movimientos originales no se borran: la reversa se registra como un movimiento
     * nuevo, para no perder la trazabilidad de lo que pasó.
     */
    private void revertirStock(Venta venta, UUID idUsuario) {
        List<MovimientoStock> movimientos =
            movimientoStockRepository.findByIdReferenciaAndTipoAndDeletedAtIsNull(
                venta.getId(), TipoMovimiento.VENTA);

        for (MovimientoStock m : movimientos) {
            int aReponer = Math.abs(m.getCantidad());
            if (aReponer == 0) continue;

            if (m.getIdLote() != null) {
                Lote lote = loteRepository.findById(m.getIdLote())
                    .orElseThrow(() -> new BadRequestException(
                        "No se puede revertir la venta: falta el lote " + m.getIdLote()));
                lote.setCantidad(lote.getCantidad() + aReponer);
                loteRepository.save(lote);

                movimientoStockRepository.save(MovimientoStock.builder()
                    .idProducto(m.getIdProducto())
                    .idLote(lote.getId())
                    .cantidad(aReponer)
                    .tipo(TipoMovimiento.AJUSTE)
                    .motivo("Reversa por anulación de venta " + venta.getId())
                    .idUsuario(idUsuario)
                    .idReferencia(venta.getId())
                    .build());
            } else {
                inventarioApi.aumentar(MovimientoStockRequest.builder()
                    .idProducto(m.getIdProducto())
                    .cantidad(aReponer)
                    .tipo("AJUSTE")
                    .motivo("Reversa por anulación de venta " + venta.getId())
                    .idUsuario(idUsuario)
                    .idReferencia(venta.getId())
                    .build());
            }
        }
    }

    @Override
    @Transactional
    public CobrarVentaResponse cobrar(UUID idVenta, UUID idUsuario, CobrarVentaRequest request) {
        Venta venta = ventaRepository.findByIdAndCobradaFalseAndDeletedAtIsNull(idVenta)
            .orElseThrow(() -> new BadRequestException("Venta no encontrada o ya está cobrada"));

        String metodoPago = normalizarMetodoPago(request.getMetodoPago());

        if (request.getMontoRecibido() < venta.getTotal()) {
            throw new BadRequestException("El monto recibido es menor al total de la venta");
        }

        // Solo hay vuelto si se paga en efectivo.
        float cambio = ES_EFECTIVO.equals(metodoPago)
            ? request.getMontoRecibido() - venta.getTotal()
            : 0;

        venta.setCobrada(true);
        venta.setFechaCobro(LocalDateTime.now());
        venta.setMetodoPago(metodoPago);
        venta.setMontoRecibido(request.getMontoRecibido());

        // Solo el efectivo entra a la caja: la tarjeta y la transferencia quedan registradas
        // en la venta (metodo_pago) pero no forman parte del arqueo, que cuenta billetes.
        // La sesión se resuelve acá, al cobrar, y nunca se acepta del cliente: así no se
        // puede imputar plata a un turno que ya cerró su corte.
        if (ES_EFECTIVO.equals(metodoPago)) {
            UUID idSesion = cajaApi.getIdSesionActiva();
            venta.setIdSesion(idSesion);
            cajaApi.registrarEntradaAutomatica(
                idSesion, idUsuario, venta.getTotal(), "VENTA", venta.getId());
        } else {
            // La venta con tarjeta o transferencia igual pertenece al turno: se la asocia
            // para poder reportarla en el cierre, pero sin generar movimiento de caja.
            cajaApi.buscarSesionActiva().ifPresent(venta::setIdSesion);
        }

        venta = ventaRepository.saveAndFlush(venta);

        List<DetalleVenta> detalles = detalleVentaRepository.findByIdVentaAndDeletedAtIsNull(venta.getId());
        VentaResponse ventaResponse = toVentaResponse(venta, toDetalleVentaResponseList(detalles));

        return CobrarVentaResponse.builder()
            .venta(ventaResponse)
            .cambio(cambio)
            .build();
    }

    /** Resumen de lo cobrado en el día, por fecha de cobro (no de creación de la venta). */
    @Override
    public ResumenDiarioResponse getResumenDiario(LocalDate fecha) {
        List<Venta> ventas = ventaRepository.findCobradasEnRango(
            fecha.atStartOfDay(), fecha.plusDays(1).atStartOfDay());
        return toResumen(fecha, ventas);
    }

    /** Mismo desglose, acotado a un turno de caja: es lo que se mira al cerrar. */
    @Override
    public ResumenDiarioResponse getResumenPorSesion(UUID idSesion) {
        return toResumen(LocalDate.now(),
            ventaRepository.findByIdSesionAndCobradaTrueAndDeletedAtIsNull(idSesion));
    }

    private ResumenDiarioResponse toResumen(LocalDate fecha, List<Venta> ventas) {
        int cantidadVentas = ventas.size();
        float totalVentas = 0;
        float totalEfectivo = 0;
        float totalTarjeta = 0;
        float totalTransferencia = 0;

        for (Venta v : ventas) {
            totalVentas += v.getTotal();
            if ("EFECTIVO".equals(v.getMetodoPago())) {
                totalEfectivo += v.getTotal();
            } else if ("TARJETA".equals(v.getMetodoPago())) {
                totalTarjeta += v.getTotal();
            } else if ("TRANSFERENCIA".equals(v.getMetodoPago())) {
                totalTransferencia += v.getTotal();
            }
        }

        return ResumenDiarioResponse.builder()
            .fecha(fecha)
            .cantidadVentas(cantidadVentas)
            .totalVentas(totalVentas)
            .totalEfectivo(totalEfectivo)
            .totalTarjeta(totalTarjeta)
            .totalTransferencia(totalTransferencia)
            .build();
    }

    // Helpers

    /**
     * Arma las respuestas de varias ventas con <b>dos</b> consultas en total, en vez de una por
     * venta: trae todos los detalles juntos y los agrupa en memoria.
     */
    private List<VentaResponse> toVentaResponseList(List<Venta> ventas) {
        if (ventas.isEmpty()) {
            return List.of();
        }

        Map<UUID, List<DetalleVenta>> porVenta = detalleVentaRepository
            .findByIdVentaInAndDeletedAtIsNull(ventas.stream().map(Venta::getId).toList())
            .stream()
            .collect(Collectors.groupingBy(DetalleVenta::getIdVenta));

        return ventas.stream()
            .map(v -> toVentaResponse(v,
                toDetalleVentaResponseList(porVenta.getOrDefault(v.getId(), List.of()))))
            .toList();
    }

    private String normalizarMetodoPago(String metodoPago) {
        String m = metodoPago == null ? "" : metodoPago.trim().toUpperCase();
        if (!List.of("EFECTIVO", "TARJETA", "TRANSFERENCIA").contains(m)) {
            throw new BadRequestException(
                "Método de pago inválido: " + metodoPago + ". Válidos: EFECTIVO, TARJETA, TRANSFERENCIA");
        }
        return m;
    }

    private VentaResponse toVentaResponse(Venta venta, List<DetalleVentaResponse> detalles) {
        VentaResponse response = new VentaResponse();
        response.setId(venta.getId());
        response.setFecha(venta.getCreatedAt());
        response.setTotal(venta.getTotal());
        response.setDetalles(detalles);
        response.setCobrada(venta.getCobrada());
        response.setFechaCobro(venta.getFechaCobro());
        response.setMetodoPago(venta.getMetodoPago());
        response.setMontoRecibido(venta.getMontoRecibido());
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
        response.setCostoUnitario(detalle.getCostoUnitario());

        return response;
    }

    private List<DetalleVentaResponse> toDetalleVentaResponseList(List<DetalleVenta> detalles) {
        return detalles.stream()
            .map(this::toDetalleVentaResponse)
            .toList();
    }
}
