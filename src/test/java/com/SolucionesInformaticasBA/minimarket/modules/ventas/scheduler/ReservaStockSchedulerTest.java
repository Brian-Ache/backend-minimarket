package com.SolucionesInformaticasBA.minimarket.modules.ventas.scheduler;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.SolucionesInformaticasBA.minimarket.modules.ventas.api.VentasApi;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.repository.VentaRepository;
import com.SolucionesInformaticasBA.minimarket.shared.exeption.BadRequestException;

@ExtendWith(MockitoExtension.class)
class ReservaStockSchedulerTest {

    @Mock private VentaRepository ventaRepository;
    @Mock private VentasApi ventasApi;

    @InjectMocks private ReservaStockScheduler scheduler;

    private void configurarMinutos(long minutos) {
        ReflectionTestUtils.setField(scheduler, "minutosDeReserva", minutos);
    }

    @Test
    @DisplayName("en 0 minutos el barrido queda apagado y no consulta nada")
    void enCeroNoHaceNada() {
        configurarMinutos(0);

        scheduler.liberarReservasVencidas();

        verify(ventaRepository, never()).findReservasVencidas(any());
        verify(ventasApi, never()).anularPorReservaVencida(any());
    }

    @Test
    @DisplayName("anula cada venta sin cobrar anterior al límite")
    void anulaLasVencidas() {
        configurarMinutos(120);
        UUID unaVenta = UUID.randomUUID();
        UUID otraVenta = UUID.randomUUID();
        when(ventaRepository.findReservasVencidas(any())).thenReturn(List.of(unaVenta, otraVenta));

        scheduler.liberarReservasVencidas();

        verify(ventasApi).anularPorReservaVencida(unaVenta);
        verify(ventasApi).anularPorReservaVencida(otraVenta);
    }

    @Test
    @DisplayName("el límite se calcula restando los minutos configurados")
    void elLimiteSaleDeLaConfiguracion() {
        configurarMinutos(90);
        when(ventaRepository.findReservasVencidas(any())).thenReturn(List.of());

        LocalDateTime antes = LocalDateTime.now().minusMinutes(90);
        scheduler.liberarReservasVencidas();
        LocalDateTime despues = LocalDateTime.now().minusMinutes(90);

        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(ventaRepository).findReservasVencidas(captor.capture());
        LocalDateTime limite = captor.getValue();
        assertTrue(!limite.isBefore(antes) && !limite.isAfter(despues),
                "el límite tiene que caer 90 minutos atrás: " + limite);
    }

    @Test
    @DisplayName("si una venta falla, las demás se liberan igual")
    void unaQueFallaNoFrenaAlResto() {
        configurarMinutos(120);
        UUID laQueFalla = UUID.randomUUID();
        UUID laQueSale = UUID.randomUUID();
        when(ventaRepository.findReservasVencidas(any())).thenReturn(List.of(laQueFalla, laQueSale));
        // Cada anulación va en su propia transacción, así que una caída no arrastra al resto.
        org.mockito.Mockito.doThrow(new BadRequestException("stock tocado por otro lado"))
                .when(ventasApi).anularPorReservaVencida(laQueFalla);

        scheduler.liberarReservasVencidas();

        verify(ventasApi).anularPorReservaVencida(laQueSale);
    }
}
