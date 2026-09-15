package com.SolucionesInformaticasBA.minimarket.modules.inventario.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * El movimiento dejó de fecharse solo con {@code @CreationTimestamp}, que pisaba el valor en
 * cada INSERT: un ticket creado sin conexión llega dos días después y el kardex tiene que
 * mostrarlo el día en que la mercadería salió del local.
 *
 * <p>Lo que queda en su lugar es un fallback, para que ningún camino online tenga que enterarse
 * del cambio.
 */
class MovimientoStockFechaTest {

    @Test
    @DisplayName("un movimiento sin fecha se fecha solo: es todo el flujo online")
    void sinFechaSeFechaSolo() {
        MovimientoStock movimiento = MovimientoStock.builder()
            .idProducto(UUID.randomUUID()).cantidad(-1).build();
        assertThat(movimiento.getCreatedAt()).isNull();

        LocalDateTime antes = LocalDateTime.now();
        movimiento.fecharSiNoVino();

        assertThat(movimiento.getCreatedAt()).isAfterOrEqualTo(antes);
    }

    @Test
    @DisplayName("y uno que ya trae fecha no se pisa")
    void conFechaNoSePisa() {
        LocalDateTime anteayer = LocalDateTime.now().minusDays(2);
        MovimientoStock movimiento = MovimientoStock.builder()
            .idProducto(UUID.randomUUID()).cantidad(-1).createdAt(anteayer).build();

        movimiento.fecharSiNoVino();

        assertThat(movimiento.getCreatedAt())
            .as("es justo lo que @CreationTimestamp hacía mal")
            .isEqualTo(anteayer);
    }
}
