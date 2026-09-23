package com.SolucionesInformaticasBA.minimarket.modules.ventas.service;

import java.math.BigDecimal;
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
import com.SolucionesInformaticasBA.minimarket.modules.caja.enums.OrigenMovimientoCaja;
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
import com.SolucionesInformaticasBA.minimarket.modules.ventas.enums.OrigenVenta;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.repository.DetalleVentaRepository;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.repository.VentaRepository;
import com.SolucionesInformaticasBA.minimarket.shared.Importes;
import com.SolucionesInformaticasBA.minimarket.shared.SecurityUtils;
import com.SolucionesInformaticasBA.minimarket.shared.Uuid7;
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
    private final CajaApi cajaApi;
    private final DescontadorStock descontadorStock;
    private final AnuladorVentas anuladorVentas;

    @Override
    @Transactional
    public VentaResponse realizarVenta(UUID idUsuario, VentaRequest request) {
        if (!usuarioApi.existById(idUsuario)) {
            throw new ResourceNotFoundException("Usuario no encontrado");
        }

        if (request.getDetalles() == null || request.getDetalles().isEmpty()) {
            throw new BadRequestException("La venta debe tener al menos un detalle");
        }

        // El id y la fecha se asignan acá y no los pone Hibernate. El id porque tiene que ser
        // un UUIDv7 —el generador de Hibernate produce v4, que fragmenta el índice—, y la
        // fecha porque created_at pasa a significar cuándo ocurrió el ticket: en una venta
        // online es ahora, pero en una offline la manda el front y es de hace días.
        LocalDateTime ocurridoEn = LocalDateTime.now();

        // Se guarda primero para tener el id: cada movimiento de stock lo referencia y así
        // la anulación puede revertir exactamente lo que esta venta descontó.
        Venta venta = ventaRepository.save(Venta.builder()
            .id(Uuid7.nuevo())
            .idUsuario(idUsuario)
            .total(Importes.CERO)
            .createdAt(ocurridoEn)
            .origen(OrigenVenta.ONLINE)
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
            detalle.setId(Uuid7.nuevo());
            detalle.setCreatedAt(ocurridoEn);
            BigDecimal precio;

            if ("PRODUCTO".equals(d.getTipo())) {
                if (d.getIdProducto() == null) {
                    throw new BadRequestException("idProducto requerido para tipo PRODUCTO");
                }

                ProductoResponse producto = productosApi.getById(d.getIdProducto());

                // El precio y el costo llegan en float desde productos, que todavía no migró.
                // Importes.de los cruza sin arrastrar la basura binaria del float.
                precio = Importes.de(producto.getPrecio());

                detalle.setIdProducto(producto.getId());
                detalle.setNombreProducto(producto.getNombre());
                // Se congela el costo de hoy: si mañana cambia, la ganancia histórica no se
                // reescribe. Los ítems MANUAL quedan sin costo (null), no en 0.
                detalle.setCostoUnitario(Importes.deNullable(producto.getCosto()));

                // El descuento vive en un colaborador porque el flujo de sincronización hace
                // exactamente lo mismo, y dos copias de este loop es la forma más probable de
                // que un día tomen los locks de lote en órdenes distintos.
                descontadorStock.descontar(producto, d.getCantidad(), idUsuario, venta.getId());

            } else if ("MANUAL".equals(d.getTipo())) {
                if (d.getNombreManual() == null || d.getNombreManual().isBlank()) {
                    throw new BadRequestException("nombreManual requerido para tipo MANUAL");
                }

                if (d.getPrecioUnitario() == null
                        || d.getPrecioUnitario().compareTo(BigDecimal.ZERO) <= 0) {
                    throw new BadRequestException("precioUnitario debe ser mayor a 0 para tipo MANUAL");
                }

                precio = Importes.normalizar(d.getPrecioUnitario());

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

        // El total se suma en el orden del ticket y no en el de bloqueo. Con BigDecimal la suma
        // es asociativa y el orden ya no cambia el resultado, pero se mantiene porque es el
        // mismo recorrido que va a hacer el front al armar su ticket: si alguna vez las dos
        // cuentas no coinciden, que no sea por el orden.
        BigDecimal total = Importes.CERO;
        for (DetalleVenta d : detalles) {
            total = total.add(Importes.porCantidad(d.getPrecioUnitario(), d.getCantidad()));
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

    /**
     * Lo que quedó marcado para mirar: regularizaciones de stock y diferencias de total.
     *
     * <p>Son ventas válidas y ya sincronizadas. Lo que corresponde hacer con una regularización
     * no es revisar el ticket sino contar ese producto, con el ajuste contra conteo físico que
     * ya existe.
     */
    @Override
    public Page<VentaResponse> getParaRevision(Pageable pageable) {
        return toVentaResponsePage(ventaRepository.findParaRevision(pageable), pageable);
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
     * Anula una venta y devuelve la mercadería al stock.
     *
     * <p><b>Una venta cobrada ahora sí se anula.</b> Estaba prohibido de forma explícita, y era
     * coherente mientras no existiera una ventana: hoy, con siete días, el 100% de lo anulable
     * está cobrado. Si movió efectivo, la plata vuelve por el turno abierto de hoy.
     *
     * <p>Quién puede anular qué lo decide {@link AnuladorVentas#validarPermiso}, que es el mismo
     * lugar por el que pasa la anulación que llega del front offline: no puede haber una puerta
     * más permisiva que la otra.
     */
    @Override
    @Transactional
    public void delete(UUID id) {
        Venta venta = ventaRepository.findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> new ResourceNotFoundException("Venta no encontrada"));

        UUID actor = SecurityUtils.getCurrentUserId();
        anuladorVentas.validarPermiso(venta, actor, SecurityUtils.esAdmin());
        anuladorVentas.anular(venta, actor, LocalDateTime.now());
    }

    /**
     * Libera la reserva de stock de una venta que quedó sin cobrar más tiempo del permitido.
     *
     * <p>Es la misma anulación que la manual, con dos diferencias: la dispara el sistema, así
     * que el movimiento de reversa queda sin usuario, y es idempotente —si la venta ya se
     * cobró o ya se anuló entre que el barrido la eligió y llegó acá, no hace nada—, porque el
     * que la llama es un job y no una persona a la que avisarle.
     */
    @Override
    @Transactional
    public void anularPorReservaVencida(UUID id) {
        Venta venta = ventaRepository.findByIdAndDeletedAtIsNull(id).orElse(null);
        if (venta == null || Boolean.TRUE.equals(venta.getCobrada())) {
            return;
        }
        anuladorVentas.anular(venta, null, LocalDateTime.now());
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
        BigDecimal montoRecibido = Importes.normalizar(request.getMontoRecibido());
        if (enEfectivo) {
            if (montoRecibido == null) {
                throw new BadRequestException("El monto recibido es obligatorio para cobrar en efectivo");
            }
            if (montoRecibido.compareTo(venta.getTotal()) < 0) {
                throw new BadRequestException("El monto recibido es menor al total de la venta");
            }
        }

        // Solo hay vuelto si se paga en efectivo. Con DECIMAL la resta es exacta: se acabaron
        // los vueltos de 149.99999 que había que redondear para mostrar.
        BigDecimal cambio = enEfectivo ? montoRecibido.subtract(venta.getTotal()) : Importes.CERO;

        venta.setCobrada(true);
        venta.setFechaCobro(LocalDateTime.now());
        venta.setMetodoPago(metodoPago);
        venta.setMontoRecibido(enEfectivo ? montoRecibido : null);

        // Solo el efectivo entra a la caja: la tarjeta y la transferencia quedan registradas
        // en la venta (metodo_pago) pero no forman parte del arqueo, que cuenta billetes.
        // La sesión se resuelve acá, al cobrar, y nunca se acepta del cliente: así no se
        // puede imputar plata a un turno que ya cerró su corte.
        if (enEfectivo) {
            UUID idSesion = cajaApi.getIdSesionActiva();
            venta.setIdSesion(idSesion);
            // Frontera con caja, que sigue llevando la plata en float: acá se pierde la
            // exactitud que la venta sí tiene. Es la deuda técnica #1 del roadmap y se salda
            // cuando migren los importes de caja y compras.
            // Fecha null: el cobro ocurre ahora, con el cajero frente a la pantalla.
            cajaApi.registrarEntradaAutomatica(
                idSesion, idUsuario, Importes.aFloat(venta.getTotal()),
                OrigenMovimientoCaja.VENTA, venta.getId(), null);
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
        BigDecimal totalVentas = Importes.CERO;
        BigDecimal totalEfectivo = Importes.CERO;
        BigDecimal totalTarjeta = Importes.CERO;
        BigDecimal totalTransferencia = Importes.CERO;

        for (Venta v : ventas) {
            totalVentas = totalVentas.add(v.getTotal());
            if ("EFECTIVO".equals(v.getMetodoPago())) {
                totalEfectivo = totalEfectivo.add(v.getTotal());
            } else if ("TARJETA".equals(v.getMetodoPago())) {
                totalTarjeta = totalTarjeta.add(v.getTotal());
            } else if ("TRANSFERENCIA".equals(v.getMetodoPago())) {
                totalTransferencia = totalTransferencia.add(v.getTotal());
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
        response.setOrigen(venta.getOrigen() == null ? null : venta.getOrigen().name());
        response.setDispositivo(venta.getDispositivo());
        response.setSincronizadoEn(venta.getSincronizadoEn());
        response.setRequiereRevision(venta.isRequiereRevision());
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
        response.setSubtotal(Importes.porCantidad(detalle.getPrecioUnitario(), detalle.getCantidad()));
        response.setCostoUnitario(detalle.getCostoUnitario());

        return response;
    }

    private List<DetalleVentaResponse> toDetalleVentaResponseList(List<DetalleVenta> detalles) {
        return detalles.stream()
            .map(this::toDetalleVentaResponse)
            .toList();
    }
}
