package com.SolucionesInformaticasBA.minimarket.modules.ventas.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.SolucionesInformaticasBA.minimarket.modules.caja.api.CajaApi;
import com.SolucionesInformaticasBA.minimarket.modules.caja.api.dto.CorteRequest;
import com.SolucionesInformaticasBA.minimarket.modules.caja.api.dto.SesionCajaResponse;
import com.SolucionesInformaticasBA.minimarket.modules.caja.enums.EstadoSesion;
import com.SolucionesInformaticasBA.minimarket.modules.caja.enums.OrigenMovimientoCaja;
import com.SolucionesInformaticasBA.minimarket.modules.productos.api.ProductosApi;
import com.SolucionesInformaticasBA.minimarket.modules.productos.api.dto.ProductoResponse;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.api.UsuarioApi;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.enums.Rol;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.api.dto.DetalleSyncRequest;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.api.dto.EventoSyncRequest;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.api.dto.ResultadoEventoSync;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.entity.AnulacionPendiente;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.entity.DetalleVenta;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.entity.Venta;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.enums.CodigoErrorSync;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.enums.OrigenVenta;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.repository.AnulacionPendienteRepository;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.repository.DetalleVentaRepository;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.repository.VentaRepository;
import com.SolucionesInformaticasBA.minimarket.shared.Importes;
import com.SolucionesInformaticasBA.minimarket.shared.Uuid7;
import com.SolucionesInformaticasBA.minimarket.shared.exeption.BadRequestException;
import com.SolucionesInformaticasBA.minimarket.shared.exeption.ForbiddenException;
import com.SolucionesInformaticasBA.minimarket.shared.exeption.ResourceNotFoundException;

import lombok.RequiredArgsConstructor;

/**
 * Aplica <b>un</b> evento del lote, en su propia transacción.
 *
 * <p>Una transacción por evento y no una por lote: un lote a medio procesar tiene que dejar la
 * base consistente, porque es exactamente lo que pasa cuando la respuesta se pierde en el
 * camino y el front reenvía. Por eso también esto es un bean aparte y no un método privado del
 * servicio que orquesta: una llamada interna no pasa por el proxy de Spring y no abriría
 * ninguna transacción nueva.
 *
 * <p>Cuando un evento no se puede aplicar, el método lanza {@link EventoSyncException}. Que sea
 * una excepción es lo que garantiza que no quede nada a medias: al salir del método
 * transaccional, todo lo que ese evento hubiera escrito se deshace.
 */
@Component
@RequiredArgsConstructor
public class ProcesadorEventoSync {

    private static final String EFECTIVO = "EFECTIVO";
    private static final List<String> METODOS_PAGO = List.of(EFECTIVO, "TARJETA", "TRANSFERENCIA");

    private final VentaRepository ventaRepository;
    private final DetalleVentaRepository detalleVentaRepository;
    private final UsuarioApi usuarioApi;
    private final ProductosApi productosApi;
    private final CajaApi cajaApi;
    private final DescontadorStock descontadorStock;
    private final AnuladorVentas anuladorVentas;
    private final AnulacionPendienteRepository anulacionPendienteRepository;

    /** Cuánto puede adelantarse el reloj de una tablet antes de que la fecha sea sospechosa. */
    @Value("${sync.desfase-maximo-minutos:5}")
    private long desfaseMaximoMinutos;

    /** Más viejo que esto no es un corte de internet, es un reloj roto. */
    @Value("${sync.antiguedad-maxima-dias:60}")
    private long antiguedadMaximaDias;

    @Transactional
    public ResultadoEventoSync procesar(EventoSyncRequest evento, String dispositivo,
                                        UUID idUsuarioSync) {
        return switch (evento.getTipo()) {
            case CREAR -> crear(evento, dispositivo, idUsuarioSync);
            case ANULAR -> anular(evento);
            case ABRIR_SESION -> abrirSesion(evento);
            case CERRAR_SESION -> cerrarSesion(evento);
        };
    }

    // ------------------------------------------------------------------------------------
    // CREAR
    // ------------------------------------------------------------------------------------

    private ResultadoEventoSync crear(EventoSyncRequest evento, String dispositivo,
                                      UUID idUsuarioSync) {
        // La idempotencia se resuelve antes que nada: el reenvío del lote entero no es el caso
        // raro, es el caso normal cuando la respuesta se pierde. Un CREAR repetido responde OK
        // sin duplicar la venta ni volver a tocar el stock.
        //
        // Y responde lo mismo que la primera vez, marca de revisión incluida. Si devolviera un
        // OK pelado, el único escenario en que la idempotencia hace falta —la respuesta original
        // se perdió— sería también el único en que el front nunca se entera de que ese ticket
        // quedó para revisar.
        var yaExistente = ventaRepository.findById(evento.getUuid());
        if (yaExistente.isPresent()) {
            return yaExistente.get().isRequiereRevision()
                ? ResultadoEventoSync.okConRevision(evento, "El ticket ya estaba sincronizado y quedó marcado para revisión")
                : ResultadoEventoSync.ok(evento);
        }

        validarUuid(evento.getUuid());
        validarFecha(evento.getOcurridoEn());
        validarPayloadDeCrear(evento);

        // Solo se exige que el vendedor exista. Si lo dieron de baja durante el corte, sus
        // tickets pendientes no pueden quedar rechazados para siempre: la mercadería salió y la
        // plata está en la caja, y la baja es un hecho posterior a la venta.
        if (!usuarioApi.existById(evento.getIdVendedor())) {
            throw new EventoSyncException(CodigoErrorSync.VENDEDOR_INEXISTENTE,
                "El vendedor del ticket no existe");
        }

        validarSesion(evento.getIdSesion());

        String metodoPago = normalizarMetodoPago(evento.getMetodoPago());

        Venta venta = ventaRepository.save(Venta.builder()
            .id(evento.getUuid())
            .idUsuario(evento.getIdVendedor())
            .total(Importes.CERO)
            .cobrada(true)
            .fechaCobro(evento.getOcurridoEn())
            .metodoPago(metodoPago)
            .montoRecibido(EFECTIVO.equals(metodoPago)
                ? Importes.normalizar(evento.getMontoRecibido()) : null)
            .idSesion(evento.getIdSesion())
            .createdAt(evento.getOcurridoEn())
            .sincronizadoEn(LocalDateTime.now())
            .origen(OrigenVenta.OFFLINE)
            .dispositivo(dispositivo)
            .idUsuarioSync(idUsuarioSync)
            .build());

        List<String> regularizaciones = new ArrayList<>();
        List<DetalleVenta> detalles = armarDetalles(evento, venta, regularizaciones);

        BigDecimal recalculado = Importes.CERO;
        for (DetalleVenta d : detalles) {
            recalculado = recalculado.add(Importes.porCantidad(d.getPrecioUnitario(), d.getCantidad()));
        }

        // El total del front se compara, no se guarda: viene sumado en otra máquina. Lo que
        // queda en la base es siempre el recalculado. Con los importes en DECIMAL las dos
        // cuentas son exactas, así que cualquier diferencia es real y hay que mirarla —ya no
        // existe la tolerancia de un centavo que hacía falta cuando esto era float—.
        boolean totalNoCoincide = evento.getTotal() != null
            && evento.getTotal().compareTo(recalculado) != 0;

        // Las dos razones por las que un ticket entra pero queda para mirar: se regularizó
        // stock (D3) o el total declarado no coincidió con el recalculado (D9). Ninguna de las
        // dos es un error: el ticket es válido y ya está sincronizado.
        venta.setTotal(recalculado);
        venta.setRequiereRevision(totalNoCoincide || !regularizaciones.isEmpty());
        venta = ventaRepository.saveAndFlush(venta);

        detalleVentaRepository.saveAll(detalles);

        registrarEnCaja(venta, metodoPago, evento);

        // Si su anulación había llegado primero, se aplica acá mismo y en esta transacción: el
        // ticket entra y sale anulado en un solo movimiento, sin quedar nunca vigente.
        aplicarAnulacionPendiente(venta);

        List<String> avisos = new ArrayList<>(regularizaciones);
        if (totalNoCoincide) {
            avisos.add("El total declarado (%s) no coincide con el recalculado (%s)"
                .formatted(evento.getTotal().toPlainString(), recalculado.toPlainString()));
        }
        if (!avisos.isEmpty()) {
            return ResultadoEventoSync.okConRevision(evento, String.join(". ", avisos));
        }
        return ResultadoEventoSync.ok(evento);
    }

    /**
     * Arma las líneas y saca la mercadería del inventario.
     *
     * <p>El recorrido va por el orden de bloqueo —idProducto ascendente, manuales al final— y no
     * por el orden del ticket, igual que la venta de mostrador y por el mismo motivo: es lo
     * único que evita que dos ventas simultáneas de los mismos productos se traben entre sí. La
     * línea vuelve después a su posición original, así que lo que se guarda sigue siendo el
     * ticket tal como salió de la caja.
     */
    private List<DetalleVenta> armarDetalles(EventoSyncRequest evento, Venta venta,
                                             List<String> regularizaciones) {
        List<DetalleSyncRequest> pedidos = evento.getDetalles();
        DetalleVenta[] procesados = new DetalleVenta[pedidos.size()];

        for (int i : ordenDeBloqueo(pedidos)) {
            DetalleSyncRequest d = pedidos.get(i);

            DetalleVenta detalle = DetalleVenta.builder()
                .id(Uuid7.nuevo())
                .idVenta(venta.getId())
                .cantidad(d.getCantidad())
                // El precio es el que el cliente pagó hace dos días, no el de la lista de hoy.
                .precioUnitario(Importes.normalizar(d.getPrecioUnitario()))
                .createdAt(evento.getOcurridoEn())
                .build();

            if ("PRODUCTO".equals(d.getTipo())) {
                ProductoResponse producto = buscarProducto(d.getIdProducto());
                detalle.setIdProducto(producto.getId());
                detalle.setNombreProducto(producto.getNombre());
                // El costo es el de hoy: es el único que tenemos. El del día de la venta se
                // perdió, y el front no lo conoce.
                detalle.setCostoUnitario(Importes.deNullable(producto.getCosto()));

                // No puede fallar por falta de stock: la mercadería salió del local hace dos
                // días. Si la existencia no alcanza, se regulariza y el ticket queda marcado.
                int regularizadas = descontadorStock.descontarRegularizando(
                    producto, d.getCantidad(), evento.getIdVendedor(), venta.getId(),
                    evento.getOcurridoEn());
                if (regularizadas > 0) {
                    regularizaciones.add("Se regularizaron %d unidades de %s"
                        .formatted(regularizadas, producto.getNombre()));
                }
            } else {
                detalle.setNombreProducto(d.getNombreManual());
            }

            procesados[i] = detalle;
        }

        return new ArrayList<>(List.of(procesados));
    }

    private ProductoResponse buscarProducto(UUID idProducto) {
        try {
            return productosApi.getById(idProducto);
        } catch (ResourceNotFoundException e) {
            throw new EventoSyncException(CodigoErrorSync.PRODUCTO_INEXISTENTE,
                "El producto " + idProducto + " no existe");
        }
    }

    /**
     * Solo el efectivo entra a la caja; la tarjeta y la transferencia quedan en la venta pero no
     * forman parte del arqueo, que cuenta billetes.
     *
     * <p>El movimiento se fecha con el <b>ocurridoEn del ticket</b>, no con el reloj del
     * servidor: la plata de un ticket de anteayer entró a la caja anteayer, y el arqueo de ese
     * turno tiene que verla ahí. Fechándola hoy, el turno viejo cerraría faltando plata y el de
     * hoy sobrando.
     */
    private void registrarEnCaja(Venta venta, String metodoPago, EventoSyncRequest evento) {
        if (!EFECTIVO.equals(metodoPago) || venta.getIdSesion() == null) {
            return;
        }
        try {
            cajaApi.registrarEntradaAutomatica(venta.getIdSesion(), evento.getIdVendedor(),
                Importes.aFloat(venta.getTotal()), OrigenMovimientoCaja.VENTA, venta.getId(),
                evento.getOcurridoEn());
        } catch (BadRequestException | ResourceNotFoundException e) {
            // La sesión se cerró entre la validación y acá, o el turno ya no admite movimientos.
            // Un corte firmado no se toca: el evento queda para que lo resuelva el admin.
            throw new EventoSyncException(CodigoErrorSync.SESION_CERRADA, e.getMessage());
        }
    }

    // ------------------------------------------------------------------------------------
    // ANULAR
    // ------------------------------------------------------------------------------------

    /**
     * Tres escenarios, y el del medio es el que obliga a que exista una tabla aparte.
     *
     * <table>
     *   <tr><td>El ticket ya está</td><td>Se anula, con la fecha de anulación del front</td></tr>
     *   <tr><td>Todavía no llegó</td><td>Queda esperándolo en {@code anulaciones_pendientes}</td></tr>
     *   <tr><td>Ya estaba anulado</td><td>{@code OK} sin efecto</td></tr>
     * </table>
     */
    private ResultadoEventoSync anular(EventoSyncRequest evento) {
        validarUuid(evento.getUuid());
        validarFecha(evento.getOcurridoEn());
        if (evento.getIdUsuario() == null) {
            throw new EventoSyncException(CodigoErrorSync.EVENTO_INVALIDO,
                "La anulación no dice quién la hizo");
        }

        Venta venta = ventaRepository.findById(evento.getUuid()).orElse(null);

        if (venta == null) {
            // El CREAR va a llegar en este mismo lote o en el siguiente. Guardar la intención y
            // responder OK es lo que hace que el front pueda sacar el evento de su cola sin
            // perderlo: si respondiéramos ERROR, el reintento dependería de que los dos eventos
            // cayeran en el mismo lote, que es justo lo que no pasó.
            anulacionPendienteRepository.save(AnulacionPendiente.builder()
                .idVenta(evento.getUuid())
                .anuladoEn(evento.getOcurridoEn())
                .idUsuario(evento.getIdUsuario())
                .motivo(evento.getMotivo())
                .createdAt(LocalDateTime.now())
                .build());
            return ResultadoEventoSync.ok(evento);
        }

        if (venta.getDeletedAt() != null) {
            return ResultadoEventoSync.ok(evento);
        }

        validarPermisoDeAnulacion(venta, evento.getIdUsuario());
        validarPosteriorAlTicket(venta, evento.getOcurridoEn());

        anular(venta, evento.getIdUsuario(), evento.getOcurridoEn());
        return ResultadoEventoSync.ok(evento);
    }

    private void aplicarAnulacionPendiente(Venta venta) {
        AnulacionPendiente pendiente = anulacionPendienteRepository.findById(venta.getId())
            .orElse(null);
        if (pendiente == null) {
            return;
        }

        // La fila se consume pase lo que pase: si la anulación no se puede aplicar, dejarla
        // esperando haría que el mismo error se repitiera en cada CREAR reenviado.
        anulacionPendienteRepository.delete(pendiente);

        validarPermisoDeAnulacion(venta, pendiente.getIdUsuario());
        validarPosteriorAlTicket(venta, pendiente.getAnuladoEn());
        anular(venta, pendiente.getIdUsuario(), pendiente.getAnuladoEn());
    }

    private void anular(Venta venta, UUID idUsuario, LocalDateTime anuladoEn) {
        try {
            anuladorVentas.anular(venta, idUsuario, anuladoEn);
        } catch (BadRequestException e) {
            // La única razón por la que la anulación de una venta en efectivo puede fallar acá
            // es que no haya ningún turno abierto donde devolver la plata. Es reintentable: en
            // cuanto abran la caja, entra.
            throw new EventoSyncException(CodigoErrorSync.SESION_CERRADA, e.getMessage());
        }
    }

    /**
     * La misma regla de permisos que el panel, resuelta sobre quien anuló <b>en el local</b>.
     *
     * <p>No se puede mirar el rol de quien está sincronizando: con una terminal y cambio de
     * turno, el que está logueado cuando vuelve internet no es el que anuló. Se usa el rol
     * vigente de esa persona, que es la mejor información que hay; un usuario que ya no existe
     * no puede haber anulado nada.
     */
    private void validarPermisoDeAnulacion(Venta venta, UUID idUsuario) {
        Rol rol = usuarioApi.rolVigente(idUsuario).orElseThrow(() -> new EventoSyncException(
            CodigoErrorSync.PERMISO_INSUFICIENTE, "Quien anuló el ticket ya no existe"));
        // Manda sobre EMPLEADO es exactamente "es ADMIN o más": ningún rol manda sobre su
        // propio nivel, así que un EMPLEADO da false.
        try {
            anuladorVentas.validarPermiso(venta, idUsuario, rol.mandaSobre(Rol.EMPLEADO));
        } catch (ForbiddenException | BadRequestException e) {
            throw new EventoSyncException(CodigoErrorSync.PERMISO_INSUFICIENTE, e.getMessage());
        }
    }

    /** Una anulación no puede ser anterior a la venta que anula: eso es un reloj roto (D8). */
    private void validarPosteriorAlTicket(Venta venta, LocalDateTime anuladoEn) {
        if (anuladoEn.isBefore(venta.getCreatedAt())) {
            throw new EventoSyncException(CodigoErrorSync.FECHA_INVALIDA,
                "La anulación es anterior al ticket que anula");
        }
    }

    // ------------------------------------------------------------------------------------
    // El turno de caja
    // ------------------------------------------------------------------------------------

    /**
     * La apertura de un turno que ocurrió sin conexión.
     *
     * <p>Sin esto, un corte largo que agarra un cambio de turno deja los tickets del turno nuevo
     * sin dónde colgarse: la sesión no existe en MySQL y la FK los rechaza. Como los eventos se
     * procesan en el orden en que pasaron en el local, el lote llega ordenado solo —apertura,
     * tickets, corte— y cuando llega el primer ticket su turno ya está.
     */
    private ResultadoEventoSync abrirSesion(EventoSyncRequest evento) {
        validarUuid(evento.getUuid());
        validarFecha(evento.getOcurridoEn());
        UUID idUsuario = exigirUsuarioDelEvento(evento);

        // Idempotencia: reenviar el lote no abre un segundo turno.
        if (sesionExiste(evento.getUuid())) {
            return ResultadoEventoSync.ok(evento);
        }

        try {
            cajaApi.abrirSesionSincronizada(evento.getUuid(), idUsuario,
                Importes.aFloat(evento.getSaldoInicial()), evento.getOcurridoEn());
        } catch (BadRequestException e) {
            // Hay otro turno abierto. Es reintentable: cuando ese cierre, esta apertura entra.
            // Con una sola caja no debería pasar; con dos cajas offline en simultáneo sí, y es
            // el límite conocido que documenta D2.
            throw new EventoSyncException(CodigoErrorSync.SESION_YA_ABIERTA, e.getMessage());
        }
        return ResultadoEventoSync.ok(evento);
    }

    /**
     * El corte de ese turno, con el conteo físico que hizo el cajero.
     *
     * <p>El saldo esperado lo calcula el backend, no el front: el dispositivo no conoce los
     * movimientos que el backend registró —una compra pagada por caja, un retiro— ni las ventas
     * de otra terminal. Lo que sí sabe, y el backend no puede saber, es cuánta plata había
     * físicamente en el cajón.
     */
    private ResultadoEventoSync cerrarSesion(EventoSyncRequest evento) {
        validarUuid(evento.getUuid());
        validarFecha(evento.getOcurridoEn());
        UUID idUsuario = exigirUsuarioDelEvento(evento);

        SesionCajaResponse sesion = buscarSesion(evento.getUuid());
        if (EstadoSesion.CERRADA.name().equals(sesion.getEstado())) {
            // Idempotencia: el corte ya hecho no se recalcula. Es además la garantía de que un
            // reenvío no puede mover un documento contable que ya está firmado.
            return ResultadoEventoSync.ok(evento);
        }
        if (evento.getOcurridoEn().isBefore(sesion.getFechaApertura())) {
            throw new EventoSyncException(CodigoErrorSync.FECHA_INVALIDA,
                "El corte es anterior a la apertura de su propio turno");
        }

        CorteRequest corte = new CorteRequest();
        corte.setSaldoReal(Importes.aFloat(evento.getSaldoFinal()));
        corte.setMontoRetirado(Importes.aFloat(evento.getMontoRetirado()));
        corte.setObservaciones(evento.getObservaciones());

        try {
            cajaApi.realizarCorteSincronizado(evento.getUuid(), idUsuario, corte,
                evento.getOcurridoEn());
        } catch (BadRequestException e) {
            throw new EventoSyncException(CodigoErrorSync.EVENTO_INVALIDO, e.getMessage());
        }
        return ResultadoEventoSync.ok(evento);
    }

    private boolean sesionExiste(UUID idSesion) {
        try {
            cajaApi.getSesionById(idSesion);
            return true;
        } catch (ResourceNotFoundException e) {
            return false;
        }
    }

    private SesionCajaResponse buscarSesion(UUID idSesion) {
        try {
            return cajaApi.getSesionById(idSesion);
        } catch (ResourceNotFoundException e) {
            // La apertura todavía no llegó: puede venir en el lote siguiente.
            throw new EventoSyncException(CodigoErrorSync.SESION_INEXISTENTE,
                "El turno que se quiere cerrar todavía no llegó");
        }
    }

    /**
     * Quién abrió o cerró el turno. Igual que el vendedor de un ticket, solo se exige que
     * exista: si lo dieron de baja durante el corte, el turno que abrió sigue siendo un hecho.
     */
    private UUID exigirUsuarioDelEvento(EventoSyncRequest evento) {
        if (evento.getIdUsuario() == null) {
            throw new EventoSyncException(CodigoErrorSync.EVENTO_INVALIDO,
                "El evento no dice qué usuario lo hizo");
        }
        if (!usuarioApi.existById(evento.getIdUsuario())) {
            throw new EventoSyncException(CodigoErrorSync.VENDEDOR_INEXISTENTE,
                "El usuario del evento no existe");
        }
        return evento.getIdUsuario();
    }

    // ------------------------------------------------------------------------------------
    // Validaciones
    // ------------------------------------------------------------------------------------

    private void validarUuid(UUID uuid) {
        if (!Uuid7.esV7(uuid)) {
            throw new EventoSyncException(CodigoErrorSync.UUID_INVALIDO,
                "El uuid tiene que ser versión 7 y es versión "
                    + (uuid == null ? "nula" : uuid.version()));
        }
    }

    /**
     * La fecha es ahora un dato del cliente, y el reloj de una tablet puede estar en 1970 o en
     * 2030. Una fecha basura envenena los reportes, el orden de los uuid v7 y la ventana de
     * anulación, así que se valida en vez de confiar.
     */
    private void validarFecha(LocalDateTime ocurridoEn) {
        LocalDateTime ahora = LocalDateTime.now();
        if (ocurridoEn.isAfter(ahora.plusMinutes(desfaseMaximoMinutos))) {
            throw new EventoSyncException(CodigoErrorSync.FECHA_INVALIDA,
                "La fecha del evento está en el futuro: revisá el reloj del dispositivo");
        }
        if (ocurridoEn.isBefore(ahora.minusDays(antiguedadMaximaDias))) {
            throw new EventoSyncException(CodigoErrorSync.FECHA_INVALIDA,
                "La fecha del evento tiene más de " + antiguedadMaximaDias + " días");
        }
    }

    private void validarPayloadDeCrear(EventoSyncRequest evento) {
        if (evento.getIdVendedor() == null) {
            throw new EventoSyncException(CodigoErrorSync.EVENTO_INVALIDO,
                "El ticket no dice quién lo vendió");
        }
        if (evento.getDetalles() == null || evento.getDetalles().isEmpty()) {
            throw new EventoSyncException(CodigoErrorSync.EVENTO_INVALIDO,
                "El ticket no tiene ninguna línea");
        }
        for (DetalleSyncRequest d : evento.getDetalles()) {
            boolean esProducto = "PRODUCTO".equals(d.getTipo());
            if (esProducto && d.getIdProducto() == null) {
                throw new EventoSyncException(CodigoErrorSync.EVENTO_INVALIDO,
                    "Una línea de tipo PRODUCTO no trae idProducto");
            }
            if (!esProducto && (d.getNombreManual() == null || d.getNombreManual().isBlank())) {
                throw new EventoSyncException(CodigoErrorSync.EVENTO_INVALIDO,
                    "Una línea de tipo MANUAL no trae nombre");
            }
            if (d.getCantidad() <= 0 || d.getPrecioUnitario() == null
                    || d.getPrecioUnitario().compareTo(BigDecimal.ZERO) < 0) {
                throw new EventoSyncException(CodigoErrorSync.EVENTO_INVALIDO,
                    "Una línea trae cantidad o precio inválidos");
            }
        }
    }

    /**
     * El ticket declara a qué turno pertenece, en vez de resolverlo con el turno abierto de
     * ahora: el turno de un ticket de hace dos días puede haber cerrado hace dos días.
     */
    private void validarSesion(UUID idSesion) {
        if (idSesion == null) {
            throw new EventoSyncException(CodigoErrorSync.EVENTO_INVALIDO,
                "El ticket no declara su sesión de caja");
        }
        SesionCajaResponse sesion;
        try {
            sesion = cajaApi.getSesionById(idSesion);
        } catch (ResourceNotFoundException e) {
            // Todavía no llegó: puede venir en el lote siguiente, así que vale reintentar.
            throw new EventoSyncException(CodigoErrorSync.SESION_INEXISTENTE,
                "La sesión de caja del ticket todavía no llegó");
        }
        if (!EstadoSesion.ABIERTA.name().equals(sesion.getEstado())) {
            throw new EventoSyncException(CodigoErrorSync.SESION_CERRADA,
                "El turno de ese ticket ya tiene su corte hecho");
        }
    }

    private String normalizarMetodoPago(String metodoPago) {
        String m = metodoPago == null ? "" : metodoPago.trim().toUpperCase();
        if (!METODOS_PAGO.contains(m)) {
            throw new EventoSyncException(CodigoErrorSync.EVENTO_INVALIDO,
                "Método de pago inválido: " + metodoPago);
        }
        return m;
    }

    /** Igual que en la venta de mostrador: idProducto ascendente, con los manuales al final. */
    private static List<Integer> ordenDeBloqueo(List<DetalleSyncRequest> detalles) {
        return IntStream.range(0, detalles.size())
            .boxed()
            .sorted(Comparator.comparing((Integer i) -> detalles.get(i).getIdProducto(),
                    Comparator.nullsLast(Comparator.naturalOrder())))
            .toList();
    }
}
