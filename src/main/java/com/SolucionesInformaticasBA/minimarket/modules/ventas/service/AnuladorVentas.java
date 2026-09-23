package com.SolucionesInformaticasBA.minimarket.modules.ventas.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.SolucionesInformaticasBA.minimarket.modules.caja.api.CajaApi;
import com.SolucionesInformaticasBA.minimarket.modules.caja.enums.OrigenMovimientoCaja;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.api.InventarioApi;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.api.dto.MovimientoStockRequest;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.entity.Lote;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.entity.MovimientoStock;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.enums.TipoMovimiento;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.repository.LoteRepository;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.repository.MovimientoStockRepository;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.entity.DetalleVenta;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.entity.Venta;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.repository.DetalleVentaRepository;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.repository.VentaRepository;
import com.SolucionesInformaticasBA.minimarket.shared.Importes;
import com.SolucionesInformaticasBA.minimarket.shared.exeption.BadRequestException;
import com.SolucionesInformaticasBA.minimarket.shared.exeption.ForbiddenException;

import lombok.RequiredArgsConstructor;

/**
 * Anula una venta: repone la mercadería, da de baja el comprobante y devuelve la plata.
 *
 * <p>Vive aparte porque hay <b>tres</b> puertas que anulan —el panel, el evento {@code ANULAR}
 * que llega del front offline, y el barrido de reservas vencidas— y las tres tienen que hacer
 * exactamente lo mismo. Sobre todo tienen que aplicar los <b>mismos permisos</b>: si la puerta
 * de sincronización fuera más permisiva que la del panel, anular offline se convertiría en la
 * forma de saltear el permiso.
 */
@Component
@RequiredArgsConstructor
public class AnuladorVentas {

    private static final String EFECTIVO = "EFECTIVO";

    private final VentaRepository ventaRepository;
    private final DetalleVentaRepository detalleVentaRepository;
    private final MovimientoStockRepository movimientoStockRepository;
    private final LoteRepository loteRepository;
    private final InventarioApi inventarioApi;
    private final CajaApi cajaApi;

    /**
     * Cuántos días hacia atrás puede anular un vendedor. Un administrador no tiene ventana.
     *
     * <p>No se confunde con los 47 días del historial local del front: eso es cuánto tiempo el
     * dispositivo puede <b>consultar</b> un ticket, no cuánto puede anularlo.
     */
    @Value("${ventas.anulacion.dias:7}")
    private long diasParaAnular;

    /**
     * La regla de quién puede anular qué, en un solo lugar.
     *
     * @param esAdmin si quien anula tiene mando de administrador. Lo resuelve cada llamador:
     *        el panel lo saca del JWT y el flujo de sincronización del rol vigente del usuario
     *        que anuló en el local, porque ahí quien llama no es quien anuló.
     */
    public void validarPermiso(Venta venta, UUID idUsuario, boolean esAdmin) {
        if (esAdmin) {
            return;
        }

        if (!venta.getIdUsuario().equals(idUsuario)) {
            throw new ForbiddenException("No tenés permiso para anular una venta de otro vendedor");
        }

        LocalDateTime referencia = fechaDeReferencia(venta);
        if (referencia.isBefore(LocalDateTime.now().minusDays(diasParaAnular))) {
            throw new BadRequestException(
                "La venta es de hace más de " + diasParaAnular
                    + " días: pedile a un administrador que la anule");
        }
    }

    /**
     * La ventana se mide sobre la <b>apertura del turno</b> de la venta y no sobre su fecha: es
     * el turno el que define el período contable, y una venta de las 23:50 pertenece al turno
     * que abrió a las 18:00.
     *
     * <p>Una venta sin sesión —las que no son en efectivo cuando no había turno abierto— cae de
     * vuelta en su propia fecha, que es lo único que tiene.
     */
    private LocalDateTime fechaDeReferencia(Venta venta) {
        if (venta.getIdSesion() == null) {
            return venta.getCreatedAt();
        }
        try {
            return cajaApi.getSesionById(venta.getIdSesion()).getFechaApertura();
        } catch (RuntimeException e) {
            return venta.getCreatedAt();
        }
    }

    /**
     * Deshace la venta.
     *
     * @param idUsuario quién anula; null cuando la dispara el barrido de reservas vencidas.
     * @param anuladoEn cuándo se anuló. En el panel es ahora; en una anulación offline la manda
     *        el front y puede ser de hace dos días (D8).
     */
    public void anular(Venta venta, UUID idUsuario, LocalDateTime anuladoEn) {
        revertirStock(venta, idUsuario, anuladoEn);
        revertirCaja(venta, idUsuario);

        venta.setDeletedAt(anuladoEn);
        ventaRepository.save(venta);

        List<DetalleVenta> detalles =
            detalleVentaRepository.findByIdVentaAndDeletedAtIsNull(venta.getId());
        for (DetalleVenta d : detalles) {
            d.setDeletedAt(anuladoEn);
        }
        detalleVentaRepository.saveAll(detalles);
    }

    /**
     * Devuelve la plata que entró por la venta, <b>al turno abierto de hoy</b>.
     *
     * <p>No al turno en el que se cobró: ese corte ya está firmado y no se toca. La plata sale
     * físicamente de la caja de hoy cuando el cliente vuelve con el ticket, así que el
     * movimiento va donde realmente ocurre y el arqueo de hoy cuadra con el efectivo real.
     *
     * <p>Es distinto de lo que hace {@code compras}, que se niega a anular si el turno cerró.
     * Acá esa regla vaciaría la ventana de siete días: al segundo día ya no habría nada
     * anulable.
     *
     * <p>Si no hay ningún turno abierto, la anulación se rechaza. Es la misma invariante de
     * siempre —nunca imputar un movimiento a un turno cerrado— y además es la verdad
     * operativa: sin caja abierta no hay de dónde sacar la plata.
     */
    private void revertirCaja(Venta venta, UUID idUsuario) {
        boolean movioPlata = Boolean.TRUE.equals(venta.getCobrada())
            && EFECTIVO.equals(venta.getMetodoPago());
        if (!movioPlata) {
            return;
        }

        UUID turnoDeHoy = cajaApi.buscarSesionActiva().orElseThrow(() -> new BadRequestException(
            "No se puede anular una venta en efectivo sin un turno de caja abierto: "
                + "abrí la caja para devolver la plata"));

        // Fecha null —o sea, ahora— aunque la anulación sea de hace dos días, y es la única
        // fecha de toda esta versión que NO sigue al front. El motivo es la decisión de arriba:
        // la plata sale del turno de hoy, así que el movimiento tiene que caer dentro de la vida
        // de ese turno. Fechándolo anteayer, el arqueo del turno lo contaría —suma por sesión—
        // pero el resumen de caja por fecha no, y las dos vistas dirían cosas distintas.
        cajaApi.registrarSalidaAutomatica(turnoDeHoy, idUsuario,
            Importes.aFloat(venta.getTotal()), OrigenMovimientoCaja.REVERSA, venta.getId(), null);
    }

    /**
     * Devuelve al stock lo que descontó la venta, apoyándose en los movimientos que la
     * referencian. Trabajar sobre los movimientos —y no sobre los detalles— es lo que permite
     * reponer cada lote exactamente en la cantidad de la que se sacó cuando el FEFO repartió
     * una línea entre varios lotes. Y es también lo que hace que un ticket con stock
     * regularizado reponga las 10 unidades que salieron, y no las 4 que el sistema creía tener.
     *
     * <p>Los movimientos originales no se borran: la reversa se registra como un movimiento
     * nuevo, para no perder la trazabilidad de lo que pasó.
     */
    private void revertirStock(Venta venta, UUID idUsuario, LocalDateTime anuladoEn) {
        List<MovimientoStock> movimientos = new ArrayList<>(
            movimientoStockRepository.findByIdReferenciaAndTipoAndDeletedAtIsNull(
                venta.getId(), TipoMovimiento.VENTA));

        // Mismo orden de bloqueo que la venta que se está anulando: por producto ascendente y,
        // dentro de cada producto, los lotes en el orden del FEFO (que es el que impone
        // findParaDescuentoFefo, ver reservarLotes). Sin esto, una anulación y una venta del
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
                    // La mercadería volvió a la góndola cuando el cliente la devolvió, que en
                    // una anulación offline fue hace dos días.
                    .createdAt(anuladoEn)
                    .build());
            } else {
                inventarioApi.aumentar(MovimientoStockRequest.builder()
                    .idProducto(m.getIdProducto())
                    .cantidad(aReponer)
                    .tipo("AJUSTE")
                    .motivo("Reversa por anulación de venta " + venta.getId())
                    .idUsuario(idUsuario)
                    .idReferencia(venta.getId())
                    .fecha(anuladoEn)
                    .build());
            }
        }
    }

    /**
     * Toma por adelantado el lock de los lotes de cada producto involucrado, en el orden del
     * FEFO. La reversa recorre movimientos, o sea un lote suelto por vez y en el orden en que
     * se vendieron; sin esta pasada previa bloquearía los lotes de un producto en un orden
     * distinto al que usa el resto del sistema, que es justo lo que abre el ciclo.
     */
    private void reservarLotes(List<MovimientoStock> movimientos) {
        movimientos.stream()
            .filter(m -> m.getIdLote() != null)
            .map(MovimientoStock::getIdProducto)
            .distinct()
            .forEach(loteRepository::findParaDescuentoFefo);
    }
}
