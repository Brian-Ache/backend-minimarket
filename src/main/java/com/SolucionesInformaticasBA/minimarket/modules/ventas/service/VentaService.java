package com.SolucionesInformaticasBA.minimarket.modules.ventas.service;

import java.time.LocalDate;
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
import com.SolucionesInformaticasBA.minimarket.shared.exeption.ForbiddenException;
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

        // El detalle se procesa por orden de bloqueo, no por el orden en que llegó, y cada
        // línea vuelve a su posición original: lo que se guarda y lo que se devuelve sigue
        // siendo el ticket tal como lo cargó el cajero.
        List<DetalleVentaRequest> pedidos = request.getDetalles();
        DetalleVenta[] procesados = new DetalleVenta[pedidos.size()];

        for (int i : ordenDeBloqueo(pedidos)) {
            DetalleVentaRequest d = pedidos.get(i);
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
                    // Con lock de fila: sin él, dos ventas simultáneas del mismo producto
                    // descontaban las dos sobre la misma cantidad leída y se vendía de más.
                    List<Lote> lotes = loteRepository.findParaDescuentoFifo(producto.getId());
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

            procesados[i] = detalle;
        }

        List<DetalleVenta> detalles = new ArrayList<>(Arrays.asList(procesados));

        // El total se suma en el orden del ticket y no en el de bloqueo: con float, cambiar el
        // orden de la suma puede correr el último centavo.
        float total = 0;
        for (DetalleVenta d : detalles) {
            total += d.getPrecioUnitario() * d.getCantidad();
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

        // Mismo alcance que los listados: un empleado ve lo suyo, un administrador ve todo. Si
        // no, alcanzaba con tener el id de una venta ajena para leerla entera.
        if (!SecurityUtils.esAdmin() && !SecurityUtils.getCurrentUserId().equals(venta.getIdUsuario())) {
            throw new ForbiddenException("No tenés permiso para ver una venta de otro usuario");
        }

        List<DetalleVenta> detalles = detalleVentaRepository.findByIdVentaAndDeletedAtIsNull(id);

        return toVentaResponse(venta, toDetalleVentaResponseList(detalles));
    }

    /**
     * Paginado y filtrando en la consulta: traía la tabla entera con findAll y descartaba las
     * anuladas en memoria. Es la tabla que más rápido crece del sistema —una fila por venta más
     * una por línea—, así que con unos meses de operación cada llamada se volvía impagable.
     */
    @Override
    public Page<VentaResponse> getAll(UUID idUsuario, Pageable pageable) {
        return toVentaResponsePage(ventaRepository.findFiltradas(idUsuario, pageable), pageable);
    }

    @Override
    public List<VentaResponse> getByFechaCobradas(LocalDateTime desde, LocalDateTime hasta) {
        validarRango(desde, hasta);
        return toVentaResponseList(ventaRepository.findCobradasEnRango(desde, hasta));
    }

    @Override
    public Page<VentaResponse> getByFecha(UUID idUsuario, LocalDateTime desde, LocalDateTime hasta,
                                          Pageable pageable) {
        validarRango(desde, hasta);
        return toVentaResponsePage(
            ventaRepository.findEnRango(idUsuario, desde, hasta, pageable), pageable);
    }

    /**
     * El rango es semiabierto, así que desde tiene que ser anterior a hasta: invertidos, o
     * iguales, la consulta no devuelve nada y el que pregunta se queda pensando que no hubo
     * ventas en vez de que se equivocó de fechas. La amplitud no se acota: los listados
     * paginan, así que un rango grande no trae más filas por request.
     */
    private void validarRango(LocalDateTime desde, LocalDateTime hasta) {
        if (!desde.isBefore(hasta)) {
            throw new BadRequestException(
                "El rango de fechas es inválido: 'desde' tiene que ser anterior a 'hasta'");
        }
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
        List<MovimientoStock> movimientos = new ArrayList<>(
            movimientoStockRepository.findByIdReferenciaAndTipoAndDeletedAtIsNull(
                venta.getId(), TipoMovimiento.VENTA));

        // Mismo orden de bloqueo que la venta que se está anulando: por producto ascendente y,
        // dentro de cada producto, los lotes en el orden del FIFO (que es el que impone
        // findParaDescuentoFifo, ver reservarLotes). Sin esto, una anulación y una venta del
        // mismo producto podían tomarse los lotes en orden cruzado y trabarse entre sí.
        movimientos.sort(Comparator.comparing(MovimientoStock::getIdProducto,
                Comparator.nullsLast(Comparator.naturalOrder())));
        reservarLotes(movimientos);

        for (MovimientoStock m : movimientos) {
            int aReponer = Math.abs(m.getCantidad());
            if (aReponer == 0) continue;

            if (m.getIdLote() != null) {
                Lote lote = loteRepository.findByIdParaActualizar(m.getIdLote())
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

    /**
     * Índices del detalle ordenados por idProducto ascendente, con los ítems MANUAL —que no
     * tocan inventario— al final.
     *
     * <p>Todas las operaciones que bloquean filas de inventario lo hacen en este orden. Sin un
     * orden único, dos ventas simultáneas de los mismos dos productos cargados al revés se
     * quedaban cada una con la fila que la otra necesitaba y la base tenía que matar una por
     * deadlock. El id del producto no significa nada, pero es igual para todos: alcanza con
     * que sea el mismo criterio en todas partes.
     */
    private static List<Integer> ordenDeBloqueo(List<DetalleVentaRequest> detalles) {
        return IntStream.range(0, detalles.size())
            .boxed()
            .sorted(Comparator.comparing((Integer i) -> detalles.get(i).getIdProducto(),
                    Comparator.nullsLast(Comparator.naturalOrder())))
            .toList();
    }

    /**
     * Toma por adelantado el lock de los lotes de cada producto involucrado, en el orden del
     * FIFO. La reversa recorre movimientos, o sea un lote suelto por vez y en el orden en que
     * se vendieron; sin esta pasada previa bloquearía los lotes de un producto en un orden
     * distinto al que usa el resto del sistema, que es justo lo que abre el ciclo.
     */
    private void reservarLotes(List<MovimientoStock> movimientos) {
        movimientos.stream()
            .filter(m -> m.getIdLote() != null)
            .map(MovimientoStock::getIdProducto)
            .distinct()
            .forEach(loteRepository::findParaDescuentoFifo);
    }

    @Override
    @Transactional
    public CobrarVentaResponse cobrar(UUID idVenta, UUID idUsuario, CobrarVentaRequest request) {
        Venta venta = ventaRepository.findByIdAndCobradaFalseAndDeletedAtIsNull(idVenta)
            .orElseThrow(() -> new BadRequestException("Venta no encontrada o ya está cobrada"));

        String metodoPago = normalizarMetodoPago(request.getMetodoPago());
        boolean enEfectivo = ES_EFECTIVO.equals(metodoPago);

        // El monto recibido es la plata que el cliente pone sobre el mostrador: se exige, se
        // valida y se guarda solo cuando se cobra en efectivo. Con tarjeta o transferencia no
        // existe tal cosa, y guardarlo dejaba la columna con un número que no significaba nada.
        if (enEfectivo) {
            if (request.getMontoRecibido() == null) {
                throw new BadRequestException("El monto recibido es obligatorio para cobrar en efectivo");
            }
            if (request.getMontoRecibido() < venta.getTotal()) {
                throw new BadRequestException("El monto recibido es menor al total de la venta");
            }
        }

        // Solo hay vuelto si se paga en efectivo.
        float cambio = enEfectivo ? request.getMontoRecibido() - venta.getTotal() : 0;

        venta.setCobrada(true);
        venta.setFechaCobro(LocalDateTime.now());
        venta.setMetodoPago(metodoPago);
        venta.setMontoRecibido(enEfectivo ? request.getMontoRecibido() : null);

        // Solo el efectivo entra a la caja: la tarjeta y la transferencia quedan registradas
        // en la venta (metodo_pago) pero no forman parte del arqueo, que cuenta billetes.
        // La sesión se resuelve acá, al cobrar, y nunca se acepta del cliente: así no se
        // puede imputar plata a un turno que ya cerró su corte.
        if (enEfectivo) {
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

    /**
     * Mismo desglose, acotado a un turno de caja: es lo que se mira al cerrar.
     *
     * <p>La fecha sale de la apertura del turno y no del reloj: un turno que abre a las 22:00 y
     * cierra a las 02:00, o cualquier consulta hecha al día siguiente, salía fechado con el día
     * en que se lo miraba y no con el que corresponde.
     */
    @Override
    public ResumenDiarioResponse getResumenPorSesion(UUID idSesion) {
        LocalDate fechaDelTurno = cajaApi.getSesionById(idSesion).getFechaApertura().toLocalDate();
        return toResumen(fechaDelTurno,
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
    /**
     * PageImpl y no page.map(): el armado necesita la página entera para traer todos los
     * detalles en una sola consulta. Mapear fila por fila volvería a un query por venta.
     */
    private Page<VentaResponse> toVentaResponsePage(Page<Venta> ventas, Pageable pageable) {
        return new PageImpl<>(toVentaResponseList(ventas.getContent()), pageable,
            ventas.getTotalElements());
    }

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
