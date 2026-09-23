package com.SolucionesInformaticasBA.minimarket.modules.ventas.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.SolucionesInformaticasBA.minimarket.modules.inventario.api.InventarioApi;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.api.dto.MovimientoStockRequest;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.entity.Lote;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.entity.MovimientoStock;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.enums.EstadoLote;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.enums.TipoMovimiento;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.repository.LoteRepository;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.repository.MovimientoStockRepository;
import com.SolucionesInformaticasBA.minimarket.modules.productos.api.dto.ProductoResponse;
import com.SolucionesInformaticasBA.minimarket.shared.exeption.BadRequestException;

import lombok.RequiredArgsConstructor;

/**
 * Saca del inventario la mercadería de una línea de venta.
 *
 * <p>Existe como colaborador aparte porque hay <b>dos</b> caminos que venden —el mostrador y el
 * lote de tickets que llega del front offline— y esta es la parte más delicada de los dos: toma
 * locks de fila sobre lotes y sobre stock. Dos copias de este loop es la forma más probable de
 * que un día tomen los locks en órdenes distintos y la base empiece a matar transacciones por
 * deadlock.
 *
 * <p>Los dos caminos se diferencian en <b>qué pasa cuando no alcanza</b>:
 *
 * <ul>
 *   <li>{@link #descontar} es la venta de mostrador: si falta stock, falla. El cajero tiene la
 *       pantalla delante y puede contar, corregir o sacar la línea.</li>
 *   <li>{@link #descontarRegularizando} es el ticket que ya ocurrió sin conexión: no puede
 *       fallar, porque la mercadería salió hace dos días. Ajusta y sigue.</li>
 * </ul>
 *
 * <p>Lo que <b>no</b> vive acá es el orden en que se recorren las líneas: eso depende de la
 * forma del pedido y lo decide cada llamador, siempre por idProducto ascendente.
 */
@Component
@RequiredArgsConstructor
public class DescontadorStock {

    private final InventarioApi inventarioApi;
    private final LoteRepository loteRepository;
    private final MovimientoStockRepository movimientoStockRepository;

    /**
     * Descuenta {@code cantidad} unidades del producto y deja el movimiento que la anulación va
     * a leer después para reponer exactamente lo que salió.
     *
     * @throws BadRequestException si no alcanzan las existencias.
     */
    public void descontar(ProductoResponse producto, int cantidad, UUID idUsuario, UUID idVenta) {
        if (producto.isManejaLotes()) {
            descontarDeLotes(producto, cantidad, idUsuario, idVenta, null, false);
        } else {
            inventarioApi.disminuir(MovimientoStockRequest.builder()
                .idProducto(producto.getId())
                .cantidad(cantidad)
                .tipo("VENTA")
                .motivo("Venta realizada")
                .idUsuario(idUsuario)
                .idReferencia(idVenta)
                .build());
        }
    }

    /**
     * Igual, pero para un ticket que ocurrió sin conexión: nunca falla por falta de stock.
     *
     * <p>Si la existencia no alcanza, entra primero un {@code AJUSTE} por la diferencia y
     * después la {@code VENTA} completa. El fundamento está en
     * {@code InventarioService.disminuirRegularizando}: esto es una caja registradora, no un
     * ecommerce, y la venta no produjo el faltante sino que lo reveló.
     *
     * @param fechaTicket cuándo se vendió, para que el FEFO no elija un lote que en ese momento
     *        todavía no estaba en el local.
     * @return cuántas unidades hubo que regularizar. Cero es el caso normal.
     */
    public int descontarRegularizando(ProductoResponse producto, int cantidad, UUID idUsuario,
                                      UUID idVenta, LocalDateTime fechaTicket) {
        if (producto.isManejaLotes()) {
            return descontarDeLotes(producto, cantidad, idUsuario, idVenta, fechaTicket, true);
        }
        return inventarioApi.disminuirRegularizando(MovimientoStockRequest.builder()
            .idProducto(producto.getId())
            .cantidad(cantidad)
            .tipo("VENTA")
            .motivo("Venta realizada")
            .idUsuario(idUsuario)
            .idReferencia(idVenta)
            .fecha(fechaTicket)
            .build());
    }

    // ------------------------------------------------------------------------------------
    // Lotes
    // ------------------------------------------------------------------------------------

    private int descontarDeLotes(ProductoResponse producto, int cantidad, UUID idUsuario,
                                 UUID idVenta, LocalDateTime fechaTicket, boolean regularizar) {
        int cantidadRestante = cantidad;

        // Con lock de fila: sin él, dos ventas simultáneas del mismo producto descontaban las
        // dos sobre la misma cantidad leída y se vendía de más. Es la única puerta para
        // bloquear los lotes de un producto, y el orden que devuelve es parte del contrato.
        List<Lote> lotes = loteRepository.findParaDescuentoFefo(producto.getId());

        Lote ultimoConsumido = null;
        for (Lote lote : ordenarParaFecha(lotes, fechaTicket)) {
            if (cantidadRestante <= 0) break;
            if (lote.getCantidad() <= 0) {
                continue;
            }

            int descontar = Math.min(lote.getCantidad(), cantidadRestante);
            lote.setCantidad(lote.getCantidad() - descontar);
            loteRepository.save(lote);
            cantidadRestante -= descontar;
            ultimoConsumido = lote;

            registrarVenta(producto, lote, descontar, idUsuario, idVenta, fechaTicket);
        }

        if (cantidadRestante == 0) {
            return 0;
        }
        if (!regularizar) {
            throw new BadRequestException(
                "Stock insuficiente en lotes para el producto " + producto.getNombre());
        }

        regularizar(producto, ultimoConsumido, cantidadRestante, idUsuario, idVenta, fechaTicket);
        return cantidadRestante;
    }

    /**
     * Las unidades que faltaban se cargan al lote que el FEFO estaba consumiendo y salen de ahí
     * mismo: es el lote del que físicamente se sacó la mercadería.
     *
     * <p>Si el producto no tiene ningún lote —o todos estaban en cero—, se crea uno sin fecha
     * de vencimiento. El modelo ya contempla ese estado ({@code SIN_FECHA}) y es lo honesto:
     * de esa mercadería no sabemos cuándo vence.
     */
    private void regularizar(ProductoResponse producto, Lote ultimoConsumido, int faltante,
                             UUID idUsuario, UUID idVenta, LocalDateTime fechaTicket) {
        Lote destino = ultimoConsumido != null ? ultimoConsumido : crearLoteSinFecha(producto);

        // Primero entra el ajuste y después sale la venta: el lote nunca pasa por un valor
        // negativo, ni siquiera en el paso intermedio.
        destino.setCantidad(destino.getCantidad() + faltante);
        loteRepository.save(destino);

        movimientoStockRepository.save(MovimientoStock.builder()
            .idProducto(producto.getId())
            .idLote(destino.getId())
            .cantidad(faltante)
            .tipo(TipoMovimiento.AJUSTE)
            .motivo("Regularización por venta offline " + idVenta)
            .idUsuario(idUsuario)
            .idReferencia(idVenta)
            .createdAt(fechaTicket)
            .build());

        destino.setCantidad(destino.getCantidad() - faltante);
        loteRepository.save(destino);

        registrarVenta(producto, destino, faltante, idUsuario, idVenta, fechaTicket);
    }

    private Lote crearLoteSinFecha(ProductoResponse producto) {
        return loteRepository.save(Lote.builder()
            .idProducto(producto.getId())
            .estado(EstadoLote.SIN_FECHA)
            .fechaVencimiento(null)
            .cantidad(0)
            .build());
    }

    private void registrarVenta(ProductoResponse producto, Lote lote, int cantidad,
                                UUID idUsuario, UUID idVenta, LocalDateTime fechaTicket) {
        movimientoStockRepository.save(MovimientoStock.builder()
            .idProducto(producto.getId())
            .idLote(lote.getId())
            .cantidad(-cantidad)
            .tipo(TipoMovimiento.VENTA)
            .motivo("Venta realizada (FEFO)")
            .idUsuario(idUsuario)
            .idReferencia(idVenta)
            // Null en el mostrador —la venta es ahora— y la fecha del ticket en el flujo
            // offline: el kardex tiene que mostrar la mercadería saliendo cuando salió.
            .createdAt(fechaTicket)
            .build());
    }

    /**
     * Prefiere los lotes que ya existían a la fecha del ticket, sin romper el FEFO dentro de
     * cada grupo.
     *
     * <p>El ticket offline no sabe de lotes, así que el FEFO se aplica al procesarlo, quizá dos
     * días después. Sin este filtro, un lote que ingresó ayer por una compra podría absorber la
     * venta de anteayer, cuando físicamente no estaba en el local. Si con los lotes viejos no
     * alcanza, cae en el resto y después en la regularización.
     *
     * <p><b>El cómo importa más que el qué.</b> El filtro se hace <b>en memoria</b> sobre lo que
     * la consulta ya bloqueó. Una segunda consulta que trajera "primero los viejos y después
     * los nuevos" tomaría los locks en un orden distinto al canónico —un lote ingresado ayer
     * puede vencer antes que uno viejo— y ahí se abre justo el ciclo de deadlock que
     * {@code findParaDescuentoFefo} documenta con cuidado. La puerta de bloqueo no se toca.
     */
    private static List<Lote> ordenarParaFecha(List<Lote> lotes, LocalDateTime fechaTicket) {
        if (fechaTicket == null) {
            return lotes;
        }

        List<Lote> existian = new ArrayList<>();
        List<Lote> posteriores = new ArrayList<>();
        for (Lote lote : lotes) {
            // Un lote sin created_at es de antes de que la columna existiera: se lo trata como
            // viejo, que es lo que es.
            if (lote.getCreatedAt() == null || !lote.getCreatedAt().isAfter(fechaTicket)) {
                existian.add(lote);
            } else {
                posteriores.add(lote);
            }
        }

        // Un lote que venció entre la venta y la sincronización no necesita tratamiento
        // especial: dentro de su grupo el FEFO lo elige primero igual, que es lo correcto,
        // porque es el que se vendió.
        existian.addAll(posteriores);
        return existian;
    }
}
