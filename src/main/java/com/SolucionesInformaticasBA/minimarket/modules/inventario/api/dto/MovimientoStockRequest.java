package com.SolucionesInformaticasBA.minimarket.modules.inventario.api.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MovimientoStockRequest {
    @NotNull
    private UUID idProducto;

    /** Siempre positiva: el signo lo pone el servicio según sea alta o baja. */
    @Positive
    private int cantidad;

    @NotBlank
    private String tipo; // COMPRA, VENTA, AJUSTE, MERMA

    private String motivo;

    /** Lo completa el controller desde el JWT; si viene en el body se ignora. */
    private UUID idUsuario;

    /** Venta o compra que originó el movimiento. Permite revertirlo al anularla. */
    private UUID idReferencia;

    /**
     * Cuándo ocurrió el movimiento en el local.
     *
     * <p>Null en todo el flujo online, donde ocurre ahora. La manda el flujo de sincronización,
     * porque un ticket creado sin conexión sacó la mercadería hace dos días y el kardex tiene
     * que mostrarlo ese día: si no, muestra la mercadería saliendo después de la venta que la
     * sacó.
     *
     * <p>No la acepta el controller: es un dato interno del servidor, igual que idUsuario y
     * idReferencia.
     */
    private LocalDateTime fecha;
}
