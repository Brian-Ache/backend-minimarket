package com.SolucionesInformaticasBA.minimarket.modules.inventario.api.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
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
}
