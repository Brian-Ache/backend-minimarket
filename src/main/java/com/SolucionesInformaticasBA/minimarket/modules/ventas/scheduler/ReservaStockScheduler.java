package com.SolucionesInformaticasBA.minimarket.modules.ventas.scheduler;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.SolucionesInformaticasBA.minimarket.modules.ventas.api.VentasApi;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.repository.VentaRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Libera el stock que retienen las ventas que quedaron sin cobrar.
 *
 * <p>El descuento de stock ocurre al armar la venta, no al cobrarla: si el cliente se va sin
 * pagar y nadie anula el ticket, esa mercadería queda reservada para siempre y desaparece del
 * stock disponible sin que nada lo muestre. El barrido las anula, con lo que la mercadería
 * vuelve y queda registrado el movimiento de reversa.
 *
 * <p>La ventana se configura con {@code ventas.reserva-stock.minutos}. En <b>0</b> el barrido
 * queda apagado y las ventas sin cobrar viven indefinidamente, que es el comportamiento que
 * había antes de esto.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReservaStockScheduler {

    private final VentaRepository ventaRepository;
    private final VentasApi ventasApi;

    @Value("${ventas.reserva-stock.minutos}")
    private long minutosDeReserva;

    @Scheduled(fixedDelayString = "${ventas.reserva-stock.revision-ms}")
    public void liberarReservasVencidas() {
        if (minutosDeReserva <= 0) {
            return;
        }

        LocalDateTime limite = LocalDateTime.now().minusMinutes(minutosDeReserva);
        List<UUID> vencidas = ventaRepository.findReservasVencidas(limite);
        if (vencidas.isEmpty()) {
            return;
        }

        int liberadas = 0;
        for (UUID idVenta : vencidas) {
            try {
                // Una por transacción: si una falla —por ejemplo porque su stock se tocó por
                // otro lado— las demás se liberan igual, y esta se reintenta en el barrido
                // siguiente.
                ventasApi.anularPorReservaVencida(idVenta);
                liberadas++;
            } catch (RuntimeException e) {
                log.warn("No se pudo liberar la reserva de la venta {}: {}", idVenta, e.getMessage());
            }
        }

        log.info("Reservas de stock liberadas: {} de {} ventas sin cobrar anteriores a {}",
                liberadas, vencidas.size(), limite);
    }
}
